package com.ontotext.trree.geosparql.lucene;

import com.ontotext.test.TemporaryLocalFolder;
import com.ontotext.trree.geosparql.CandidateLookupPolicy;
import com.ontotext.trree.geosparql.GeoSparqlConfig;
import com.ontotext.trree.geosparql.EntityGeometryIterator;
import com.ontotext.trree.geosparql.EntityIdIterator;
import com.ontotext.trree.geosparql.GeoSparqlPlugin;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.sdk.PluginException;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.locationtech.jts.geom.Geometry;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author Tsvetan Dimitrov <tsvetan.dimitrov@ontotext.com>
 * @since 14 Sep 2015.
 */
public class LuceneGeoIndexerTest {

    private static final Logger LOG = LoggerFactory.getLogger(LuceneGeoIndexerTest.class);
    private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
    private static final String PROJECTED_POINT_WKT =
            "<" + EPSG_32634 + "> POINT(799997.80 4589779.63)";

    @Rule
    public TemporaryLocalFolder tmpFolder = new TemporaryLocalFolder();

    private LuceneGeoIndexer luceneGeoIndexer;

    private List<IndexGeometry> geometries;

    private IndexSearcher indexSearcher;

    private IndexReader indexReader;

    private Map<Long, Long> expected = new HashMap<>();

    @Before
    public void init() throws Exception {
        initIndexer();

        geometries = new ArrayList<>();

        wkt2Geometry();

        indexGeometries();

        FSDirectory dir = FSDirectory.open(GeoSparqlConfig.resolveIndexPath(tmpFolder.getRoot().toPath()));
        indexReader = DirectoryReader.open(dir);
        indexSearcher = new IndexSearcher(indexReader);
    }

    private void indexGeometries() throws Exception {
        for (long i = 1, c = 1; i < geometries.size(); i++) {
            luceneGeoIndexer.indexGeometryList(i, (subject) -> "Subject " + subject, geometries);
            for (int k = 0; k < geometries.size(); k++) {
                expected.put(c , i);
                c++;
            }
        }
        luceneGeoIndexer.commit();
    }

    private void initIndexer() throws Exception {
        luceneGeoIndexer = createIndexer(tmpFolder.getRoot());
        luceneGeoIndexer.begin();
    }

    private LuceneGeoIndexer createIndexer(File dataDir) throws Exception {
        final GeoSparqlPlugin parent = new GeoSparqlPlugin();
        parent.setConfig(new GeoSparqlConfig());
        parent.setLogger(LOG);
        parent.setDataDir(dataDir);

        LuceneGeoIndexer indexer = new LuceneGeoIndexer(parent);
        indexer.initialize();
        return indexer;
    }

    private CountingLuceneGeoIndexer createCountingIndexer(File dataDir) throws Exception {
        final GeoSparqlPlugin parent = new GeoSparqlPlugin();
        parent.setConfig(new GeoSparqlConfig());
        parent.setLogger(LOG);
        parent.setDataDir(dataDir);

        CountingLuceneGeoIndexer indexer = new CountingLuceneGeoIndexer(parent);
        indexer.initialize();
        return indexer;
    }

    private FailingCommitLuceneGeoIndexer createFailingCommitIndexer(File dataDir) throws Exception {
        final GeoSparqlPlugin parent = new GeoSparqlPlugin();
        parent.setConfig(new GeoSparqlConfig());
        parent.setLogger(LOG);
        parent.setDataDir(dataDir);

        FailingCommitLuceneGeoIndexer indexer = new FailingCommitLuceneGeoIndexer(parent);
        indexer.initialize();
        return indexer;
    }

    private void wkt2Geometry() throws IOException {
        final List<String> wktLines = IOUtils.readLines(
                LuceneGeoIndexerTest.class.getResourceAsStream("/example_data.wkt"));
        for (String line : wktLines) {
            geometries.add(IndexGeometry.fromSourceGeometryLiteral(SourceGeometryLiteral.fromWkt(line)));
        }
    }

    @After
    public void deleteIndex() throws IOException {
        if (indexReader != null) {
            indexReader.close();
        }
    }

    @Test
    public void testDocumentIds() throws Exception {
        TopDocs docs = indexSearcher.search(new MatchAllDocsQuery(), 100);

        assertEquals(docs.scoreDocs.length, 56);

        for (int i = 1; i <= docs.scoreDocs.length; i++) {
            assertEquals((long) expected.get((long) i), getDocId(docs.scoreDocs[i - 1]));
        }
    }

    @Test
    public void testDocumentsStoreSchemaV2EffectiveSourceCrsMetadata() throws Exception {
        TopDocs docs = indexSearcher.search(new MatchAllDocsQuery(), 1);
        Document doc = indexReader.document(docs.scoreDocs[0].doc);

        assertEquals(LuceneGeoDocumentSchema.SCHEMA_VERSION,
                doc.getField(LuceneGeoDocumentSchema.FIELD_SCHEMA_VERSION).numericValue().intValue());
        assertNotNull(doc.get(LuceneGeoDocumentSchema.FIELD_SOURCE_LEXICAL_FORM));
        assertEquals(GeoConstants.GEO_WKT_LITERAL.stringValue(),
                doc.get(LuceneGeoDocumentSchema.FIELD_SOURCE_DATATYPE));
        assertEquals(IndexGeometry.INDEX_CRS, doc.get(LuceneGeoDocumentSchema.FIELD_SOURCE_CRS));
        assertEquals(IndexGeometry.INDEX_CRS, doc.get(LuceneGeoDocumentSchema.FIELD_INDEX_CRS));
        assertEquals(IndexGeometry.BUILD_MODE_TRANSFORMED_GEOMETRY,
                doc.get(LuceneGeoDocumentSchema.FIELD_INDEX_BUILD_MODE));
        assertTrue(hasRequiredEntityIdDocValues(indexReader));
        assertCurrentSchemaCommitData(indexReader);
    }

    @Test
    public void testFullScanCandidateLookupScansAllDocuments() throws Exception {
        EntityGeometryIterator iterator = luceneGeoIndexer.getMatchingObjects(geometries.get(0).indexGeometry(),
                CandidateLookupPolicy.FULL_SCAN);

        int count = 0;
        try {
            while (iterator.hasNextGeometry()) {
                assertNotNull(iterator.nextGeometry());
                assertNotNull(iterator.lastSourceGeometryLiteral());
                count++;
            }
        } finally {
            iterator.close();
        }

        assertEquals(56, count);
    }

    @Test
    public void matchingEntityIdsDeduplicateMultipleMatchingGeometryDocumentsForOneEntity() throws Exception {
        IndexGeometry geometry = IndexGeometry.fromSourceGeometryLiteral(SourceGeometryLiteral.fromWkt("POINT(0 0)"));
        Path entityCandidateDataDir = tmpFolder.getRoot().toPath().resolve("entity-candidates");
        Files.createDirectories(entityCandidateDataDir);

        LuceneGeoIndexer entityCandidateIndexer = createIndexer(entityCandidateDataDir.toFile());
        entityCandidateIndexer.begin();
        entityCandidateIndexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(geometry, geometry));
        entityCandidateIndexer.commit();

        EntityIdIterator iterator = entityCandidateIndexer.getMatchingEntityIds(geometry.indexGeometry(),
                CandidateLookupPolicy.INTERSECTS);

        assertArrayEquals(new long[]{1L}, collectEntityIds(iterator));
    }

    @Test
    public void candidateEntityGeometryLookupUsesCandidateReader() throws Exception {
        IndexGeometry geometry = IndexGeometry.fromSourceGeometryLiteral(SourceGeometryLiteral.fromWkt("POINT(0 0)"));
        Path entityCandidateDataDir = tmpFolder.getRoot().toPath().resolve("candidate-entity-geometries");
        Files.createDirectories(entityCandidateDataDir);

        CountingLuceneGeoIndexer entityCandidateIndexer = createCountingIndexer(entityCandidateDataDir.toFile());
        entityCandidateIndexer.begin();
        for (long subject = 1; subject <= 3; subject++) {
            entityCandidateIndexer.indexGeometryList(subject, id -> "Subject " + id, List.of(geometry));
        }
        entityCandidateIndexer.commit();

        EntityIdIterator iterator = entityCandidateIndexer.getMatchingEntityIds(geometry.indexGeometry(),
                CandidateLookupPolicy.FULL_SCAN);

        try {
            for (int expectedEntityId = 1; expectedEntityId <= 3; expectedEntityId++) {
                assertTrue(iterator.hasNextEntityId());
                assertEquals(expectedEntityId, iterator.nextEntityId());
                assertEntityGeometryCount(1, iterator.getGeometriesForLastEntity());
            }
            assertEquals(1, entityCandidateIndexer.openReaderCount());
        } finally {
            iterator.close();
        }
    }

    @Test
    public void candidateEntityGeometryLookupDoesNotRunEntitySearchPerCandidate() throws Exception {
        IndexGeometry geometry = IndexGeometry.fromSourceGeometryLiteral(SourceGeometryLiteral.fromWkt("POINT(0 0)"));
        try (Directory directory = new ByteBuffersDirectory();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            writer.addDocument(currentSchemaDocument(1L, geometry));
            writer.addDocument(currentSchemaDocument(2L, geometry));
            writer.addDocument(currentSchemaDocument(3L, geometry));
            writer.commit();

            try (IndexReader reader = DirectoryReader.open(directory)) {
                CountingIndexSearcher searcher = new CountingIndexSearcher(reader);
                EntityIdIterator iterator = new LuceneEntityIdIterator(searcher, new MatchAllDocsQuery());

                try {
                    for (int expectedEntityId = 1; expectedEntityId <= 3; expectedEntityId++) {
                        assertTrue(iterator.hasNextEntityId());
                        assertEquals(expectedEntityId, iterator.nextEntityId());
                        assertEntityGeometryCount(1, iterator.getGeometriesForLastEntity());
                    }
                    assertEquals(1, searcher.searchCount());
                } finally {
                    iterator.close();
                }
            }
        }
    }

    @Test
    public void candidateEntityGeometryLookupGroupsInterleavedDocumentsByEntity() throws Exception {
        IndexGeometry firstEntityGeometry1 = IndexGeometry.fromSourceGeometryLiteral(
                SourceGeometryLiteral.fromWkt("POINT(0 0)"));
        IndexGeometry secondEntityGeometry = IndexGeometry.fromSourceGeometryLiteral(
                SourceGeometryLiteral.fromWkt("POINT(1 1)"));
        IndexGeometry firstEntityGeometry2 = IndexGeometry.fromSourceGeometryLiteral(
                SourceGeometryLiteral.fromWkt("POINT(2 2)"));

        try (Directory directory = new ByteBuffersDirectory();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            writer.addDocument(currentSchemaDocument(1L, firstEntityGeometry1));
            writer.addDocument(currentSchemaDocument(2L, secondEntityGeometry));
            writer.addDocument(currentSchemaDocument(1L, firstEntityGeometry2));
            writer.commit();

            try (IndexReader reader = DirectoryReader.open(directory)) {
                EntityIdIterator iterator = new LuceneEntityIdIterator(new IndexSearcher(reader),
                        new MatchAllDocsQuery());

                try {
                    assertTrue(iterator.hasNextEntityId());
                    assertEquals(1L, iterator.nextEntityId());
                    assertEntityGeometryCount(2, iterator.getGeometriesForLastEntity());

                    assertTrue(iterator.hasNextEntityId());
                    assertEquals(2L, iterator.nextEntityId());
                    assertEntityGeometryCount(1, iterator.getGeometriesForLastEntity());
                } finally {
                    iterator.close();
                }
            }
        }
    }

    @Test
    public void matchingEntityIdsPagesPastLuceneTotalHitsLowerBound() throws Exception {
        assertFullScanReturnsEntityIdCount(2500);
    }

    @Test
    public void matchingEntityIdsHandlesExactPageMultiple() throws Exception {
        assertFullScanReturnsEntityIdCount(2000);
    }

    @Test
    public void fullScanCandidateLookupPagesPastLuceneTotalHitsLowerBound() throws Exception {
        assertFullScanReturnsDocumentCount(2500);
    }

    @Test
    public void fullScanCandidateLookupHandlesExactPageMultiple() throws Exception {
        assertFullScanReturnsDocumentCount(2000);
    }

    private void assertFullScanReturnsDocumentCount(int documentCount) throws Exception {
        IndexGeometry geometry = IndexGeometry.fromSourceGeometryLiteral(SourceGeometryLiteral.fromWkt("POINT(0 0)"));
        try (Directory directory = new ByteBuffersDirectory();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            for (long subject = 1; subject <= documentCount; subject++) {
                writer.addDocument(currentSchemaDocument(subject, geometry));
            }
            writer.commit();

            try (IndexReader reader = DirectoryReader.open(directory)) {
                EntityGeometryIterator iterator = new LuceneEntityGeometryIterator(new IndexSearcher(reader),
                        new MatchAllDocsQuery());

                int count = 0;
                try {
                    while (iterator.hasNextGeometry()) {
                        assertNotNull(iterator.nextGeometry());
                        assertNotNull(iterator.lastSourceGeometryLiteral());
                        count++;
                    }
                } finally {
                    iterator.close();
                }

                assertEquals(documentCount, count);
            }
        }
    }

    private void assertFullScanReturnsEntityIdCount(int documentCount) throws Exception {
        IndexGeometry geometry = IndexGeometry.fromSourceGeometryLiteral(SourceGeometryLiteral.fromWkt("POINT(0 0)"));
        try (Directory directory = new ByteBuffersDirectory();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            for (long subject = 1; subject <= documentCount; subject++) {
                writer.addDocument(currentSchemaDocument(subject, geometry));
            }
            writer.commit();

            try (IndexReader reader = DirectoryReader.open(directory)) {
                EntityIdIterator iterator = new LuceneEntityIdIterator(new IndexSearcher(reader),
                        new MatchAllDocsQuery());

                int count = 0;
                try {
                    while (iterator.hasNextEntityId()) {
                        assertEquals(count + 1, iterator.nextEntityId());
                        count++;
                    }
                } finally {
                    iterator.close();
                }

                assertEquals(documentCount, count);
            }
        }
    }

    private long[] collectEntityIds(EntityIdIterator iterator) throws IOException {
        try {
            long[] ids = new long[16];
            int size = 0;
            while (iterator.hasNextEntityId()) {
                if (size == ids.length) {
                    long[] expanded = new long[ids.length * 2];
                    System.arraycopy(ids, 0, expanded, 0, ids.length);
                    ids = expanded;
                }
                ids[size++] = iterator.nextEntityId();
            }

            long[] result = new long[size];
            System.arraycopy(ids, 0, result, 0, size);
            return result;
        } finally {
            iterator.close();
        }
    }

    private void assertEntityGeometryCount(int expected, EntityGeometryIterator iterator) throws IOException {
        try {
            int count = 0;
            while (iterator.hasNextGeometry()) {
                assertNotNull(iterator.nextGeometry());
                assertNotNull(iterator.lastSourceGeometryLiteral());
                count++;
            }
            assertEquals(expected, count);
        } finally {
            iterator.close();
        }
    }

    private static final class CountingLuceneGeoIndexer extends LuceneGeoIndexer {
        private int openReaderCount;

        private CountingLuceneGeoIndexer(GeoSparqlPlugin parent) {
            super(parent);
        }

        @Override
        IndexReader openReader() throws IOException {
            openReaderCount++;
            return super.openReader();
        }

        private int openReaderCount() {
            return openReaderCount;
        }
    }

    private static final class CountingIndexSearcher extends IndexSearcher {
        private int searchCount;

        private CountingIndexSearcher(IndexReader reader) {
            super(reader);
        }

        @Override
        public TopDocs search(Query query, int n) throws IOException {
            searchCount++;
            return super.search(query, n);
        }

        @Override
        public TopFieldDocs search(Query query, int n, Sort sort) throws IOException {
            searchCount++;
            return super.search(query, n, sort);
        }

        private int searchCount() {
            return searchCount;
        }
    }

    private static final class FailingCommitLuceneGeoIndexer extends LuceneGeoIndexer {
        private boolean failCommitClose;

        private FailingCommitLuceneGeoIndexer(GeoSparqlPlugin parent) {
            super(parent);
        }

        @Override
        void closeIndexWriter() throws IOException {
            if (failCommitClose) {
                throw new IOException("Simulated commit failure");
            }
            super.closeIndexWriter();
        }

        private void failCommitClose() {
            failCommitClose = true;
        }
    }

    private Document currentSchemaDocument(long entityId, IndexGeometry geometry) throws IOException {
        Document doc = new Document();
        doc.add(new LongPoint(LuceneGeoDocumentSchema.FIELD_ID, entityId));
        doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_ID, entityId));
        doc.add(new NumericDocValuesField(LuceneGeoDocumentSchema.FIELD_ID, entityId));
        doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_INDEX_GEOMETRY,
                serializeGeometry(geometry.indexGeometry())));
        doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_SCHEMA_VERSION,
                LuceneGeoDocumentSchema.SCHEMA_VERSION));
        doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_SOURCE_LEXICAL_FORM,
                geometry.sourceGeometryLiteral().lexicalForm()));
        doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_SOURCE_DATATYPE,
                GeoConstants.GEO_WKT_LITERAL.stringValue()));
        doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_SOURCE_CRS,
                geometry.sourceGeometryLiteral().effectiveCrsUri()));
        doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_INDEX_CRS, IndexGeometry.INDEX_CRS));
        doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_INDEX_BUILD_MODE,
                IndexGeometry.BUILD_MODE_TRANSFORMED_GEOMETRY));
        return doc;
    }

    @Test
    public void testIndexWithoutSchemaV2MetadataRequiresReindex() throws Exception {
        Path nonCurrentDataDir = tmpFolder.getRoot().toPath().resolve("non-current-schema");
        Files.createDirectories(nonCurrentDataDir);
        writeIndexWithoutSchemaV2Metadata(nonCurrentDataDir);

        LuceneGeoIndexer indexer = createIndexer(nonCurrentDataDir.toFile());

        PluginException exception = assertThrows(PluginException.class, () -> indexer.getGeometriesFor(0));

        assertForceReindexMessage(exception);
    }

    @Test
    public void testMarkedIndexMissingRequiredSchemaV2FieldFailsDuringDocumentDecoding() throws Exception {
        Path malformedDataDir = tmpFolder.getRoot().toPath().resolve("malformed-schema-v2");
        Files.createDirectories(malformedDataDir);
        writeMarkedIndexMissingRequiredSchemaV2Field(malformedDataDir);

        LuceneGeoIndexer indexer = createIndexer(malformedDataDir.toFile());
        EntityGeometryIterator iterator = indexer.getGeometriesFor(0);

        try {
            PluginException exception = assertThrows(PluginException.class, () -> iterator.nextGeometry());

            assertForceReindexMessage(exception);
        } finally {
            iterator.close();
        }
    }

    @Test
    public void testNonEmptyIndexMissingCommitSchemaMarkerRequiresReindex() throws Exception {
        Path missingMarkerDataDir = tmpFolder.getRoot().toPath().resolve("missing-commit-marker");
        Files.createDirectories(missingMarkerDataDir);
        writeCurrentSchemaIndexWithoutCommitMarker(missingMarkerDataDir);

        LuceneGeoIndexer indexer = createIndexer(missingMarkerDataDir.toFile());

        PluginException exception = assertThrows(PluginException.class, () -> indexer.getGeometriesFor(0));

        assertForceReindexMessage(exception);
    }

    @Test
    public void testMarkedCurrentSchemaV2IndexPassesStartupGate() throws Exception {
        Path markedDataDir = tmpFolder.getRoot().toPath().resolve("marked-schema-v2");
        Files.createDirectories(markedDataDir);
        writeMarkedCurrentSchemaIndex(markedDataDir);

        LuceneGeoIndexer indexer = createIndexer(markedDataDir.toFile());
        EntityGeometryIterator iterator = indexer.getGeometriesFor(0);

        try {
            assertTrue(iterator.hasNextGeometry());
            assertNotNull(iterator.nextGeometry());
            assertNotNull(iterator.lastSourceGeometryLiteral());
        } finally {
            iterator.close();
        }
    }

    @Test
    public void testEmptyIndexWithoutCommitSchemaMarkerAcceptsV2WritesAndGainsMarker() throws Exception {
        Path emptyDataDir = tmpFolder.getRoot().toPath().resolve("empty-no-marker");
        Files.createDirectories(emptyDataDir);
        writeEmptyIndexWithoutCommitMarker(emptyDataDir);

        LuceneGeoIndexer indexer = createIndexer(emptyDataDir.toFile());
        indexer.begin();
        indexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(geometries.get(0)));
        indexer.commit();

        try (FSDirectory dir = FSDirectory.open(GeoSparqlConfig.resolveIndexPath(emptyDataDir));
             IndexReader reader = DirectoryReader.open(dir)) {
            assertEquals(1, reader.numDocs());
            assertCurrentSchemaCommitData(reader);
        }
    }

    @Test
    public void testCurrentSchemaV2SegmentsPassEntityIdDocValuesValidation() throws Exception {
        try (Directory directory = new ByteBuffersDirectory();
             IndexWriter writer = new IndexWriter(directory, noMergeIndexWriterConfig())) {
            writer.addDocument(currentSchemaDocument(1L, geometries.get(0)));
            writer.commit();
            writer.addDocument(currentSchemaDocument(2L, geometries.get(0)));
            writer.commit();

            try (IndexReader reader = DirectoryReader.open(directory)) {
                assertTrue(hasRequiredEntityIdDocValues(reader));
            }
        }
    }

    @Test
    public void testIndexWritesFailWhenCommitSchemaMarkerMissingRequiresReindex() throws Exception {
        Path missingMarkerDataDir = tmpFolder.getRoot().toPath().resolve("mismatched-write");
        Files.createDirectories(missingMarkerDataDir);
        writeCurrentSchemaIndexWithoutCommitMarker(missingMarkerDataDir);

        LuceneGeoIndexer indexer = createIndexer(missingMarkerDataDir.toFile());
        indexer.begin();
        try {
            PluginException exception = assertThrows(PluginException.class, () ->
                    indexer.indexGeometryList(2L, subject -> "Subject " + subject, List.of(geometries.get(0))));

            assertForceReindexMessage(exception);
        } finally {
            indexer.rollback();
        }
    }

    @Test
    public void testForceReindexRollbackRestoresSchemaMismatchGate() throws Exception {
        Path missingMarkerDataDir = tmpFolder.getRoot().toPath().resolve("rollback-reindex-mismatch");
        Files.createDirectories(missingMarkerDataDir);
        writeCurrentSchemaIndexWithoutCommitMarker(missingMarkerDataDir);

        LuceneGeoIndexer indexer = createIndexer(missingMarkerDataDir.toFile());
        indexer.begin();
        indexer.freshIndex();
        indexer.rollback();

        PluginException exception = assertThrows(PluginException.class, () -> indexer.getGeometriesFor(0));

        assertForceReindexMessage(exception);
    }

    @Test
    public void testForceReindexInProgressKeepsReadsBlockedUntilCommit() throws Exception {
        Path missingMarkerDataDir = tmpFolder.getRoot().toPath().resolve("in-progress-reindex-mismatch");
        Files.createDirectories(missingMarkerDataDir);
        writeCurrentSchemaIndexWithoutCommitMarker(missingMarkerDataDir);

        LuceneGeoIndexer indexer = createIndexer(missingMarkerDataDir.toFile());
        indexer.begin();
        try {
            indexer.freshIndex();
            indexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(geometries.get(0)));

            PluginException exception = assertThrows(PluginException.class, () -> indexer.getGeometriesFor(0));

            assertForceReindexMessage(exception);
        } finally {
            indexer.rollback();
        }
    }

    @Test
    public void testForceReindexFailedCommitRestoresSchemaMismatchGate() throws Exception {
        Path missingMarkerDataDir = tmpFolder.getRoot().toPath().resolve("failed-commit-reindex-mismatch");
        Files.createDirectories(missingMarkerDataDir);
        writeCurrentSchemaIndexWithoutCommitMarker(missingMarkerDataDir);

        FailingCommitLuceneGeoIndexer indexer = createFailingCommitIndexer(missingMarkerDataDir.toFile());
        indexer.begin();
        indexer.freshIndex();
        indexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(geometries.get(0)));
        indexer.failCommitClose();

        assertThrows(IOException.class, indexer::commit);
        PluginException exception = assertThrows(PluginException.class, () -> indexer.getGeometriesFor(0));

        assertForceReindexMessage(exception);
        indexer.rollback();
    }

    @Test
    public void testSuccessfulForceReindexFromSchemaMismatchClearsGateAndWritesMarker() throws Exception {
        Path missingMarkerDataDir = tmpFolder.getRoot().toPath().resolve("successful-reindex-mismatch");
        Files.createDirectories(missingMarkerDataDir);
        writeCurrentSchemaIndexWithoutCommitMarker(missingMarkerDataDir);

        LuceneGeoIndexer indexer = createIndexer(missingMarkerDataDir.toFile());
        indexer.begin();
        indexer.freshIndex();
        indexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(geometries.get(0)));
        indexer.commit();

        EntityGeometryIterator iterator = indexer.getGeometriesFor(0);
        try {
            assertTrue(iterator.hasNextGeometry());
            assertNotNull(iterator.nextGeometry());
            assertNotNull(iterator.lastSourceGeometryLiteral());
        } finally {
            iterator.close();
        }

        try (FSDirectory dir = FSDirectory.open(GeoSparqlConfig.resolveIndexPath(missingMarkerDataDir));
             IndexReader reader = DirectoryReader.open(dir)) {
            assertEquals(1, reader.numDocs());
            assertCurrentSchemaCommitData(reader);
        }
    }

    @Test
    public void testProjectedDocumentStoresEffectiveSourceCrsAndCrs84IndexCrs() throws Exception {
        Path projectedDataDir = tmpFolder.getRoot().toPath().resolve("projected");
        Files.createDirectories(projectedDataDir);

        LuceneGeoIndexer projectedIndexer = createIndexer(projectedDataDir.toFile());
        projectedIndexer.begin();
        IndexGeometry projectedGeometry = IndexGeometry.fromSourceGeometryLiteral(
                SourceGeometryLiteral.fromWkt(PROJECTED_POINT_WKT));
        projectedIndexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(projectedGeometry));
        projectedIndexer.commit();

        try (FSDirectory dir = FSDirectory.open(GeoSparqlConfig.resolveIndexPath(projectedDataDir));
             IndexReader reader = DirectoryReader.open(dir)) {
            Document doc = reader.document(0);
            assertEquals(PROJECTED_POINT_WKT, doc.get(LuceneGeoDocumentSchema.FIELD_SOURCE_LEXICAL_FORM));
            assertEquals(GeoConstants.GEO_WKT_LITERAL.stringValue(),
                    doc.get(LuceneGeoDocumentSchema.FIELD_SOURCE_DATATYPE));
            assertEquals(EPSG_32634, doc.get(LuceneGeoDocumentSchema.FIELD_SOURCE_CRS));
            assertEquals(IndexGeometry.INDEX_CRS, doc.get(LuceneGeoDocumentSchema.FIELD_INDEX_CRS));
            assertEquals(IndexGeometry.BUILD_MODE_TRANSFORMED_GEOMETRY,
                    doc.get(LuceneGeoDocumentSchema.FIELD_INDEX_BUILD_MODE));
        }
    }

    private long getDocId(ScoreDoc docs) throws IOException {
        return indexReader.document(docs.doc).getField(LuceneGeoDocumentSchema.FIELD_ID).numericValue().longValue();
    }

    private void assertForceReindexMessage(PluginException exception) {
        assertTrue(exception.getMessage().contains("current schema v2"));
        assertTrue(exception.getMessage().contains("force-reindex"));
    }

    private void assertCurrentSchemaCommitData(IndexReader reader) throws IOException {
        assertEquals(LuceneGeoDocumentSchema.COMMIT_SCHEMA_VERSION_VALUE,
                ((DirectoryReader) reader).getIndexCommit().getUserData()
                        .get(LuceneGeoDocumentSchema.COMMIT_SCHEMA_VERSION_KEY));
    }

    private boolean hasRequiredEntityIdDocValues(IndexReader reader) {
        for (LeafReaderContext context : reader.leaves()) {
            if (context.reader().numDocs() == 0) {
                continue;
            }
            FieldInfo entityIdField = context.reader().getFieldInfos().fieldInfo(LuceneGeoDocumentSchema.FIELD_ID);
            if (entityIdField == null || entityIdField.getDocValuesType() != DocValuesType.NUMERIC) {
                return false;
            }
        }
        return true;
    }

    private void writeIndexWithoutSchemaV2Metadata(Path dataDir) throws Exception {
        Path indexDir = GeoSparqlConfig.resolveIndexPath(dataDir);
        Files.createDirectories(indexDir);
        try (FSDirectory dir = FSDirectory.open(indexDir);
            IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
            Document doc = new Document();
            doc.add(new LongPoint(LuceneGeoDocumentSchema.FIELD_ID, 1L));
            doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_ID, 1L));
            doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_INDEX_GEOMETRY,
                    serializeGeometry(geometries.get(0).indexGeometry())));
            writer.addDocument(doc);
        }
    }

    private void writeMarkedIndexMissingRequiredSchemaV2Field(Path dataDir) throws Exception {
        Path indexDir = GeoSparqlConfig.resolveIndexPath(dataDir);
        Files.createDirectories(indexDir);
        IndexGeometry geometry = geometries.get(0);
        try (FSDirectory dir = FSDirectory.open(indexDir);
            IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
            Document doc = new Document();
            doc.add(new LongPoint(LuceneGeoDocumentSchema.FIELD_ID, 1L));
            doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_ID, 1L));
            doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_INDEX_GEOMETRY,
                    serializeGeometry(geometry.indexGeometry())));
            doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_SCHEMA_VERSION,
                    LuceneGeoDocumentSchema.SCHEMA_VERSION));
            doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_SOURCE_LEXICAL_FORM,
                    geometry.sourceGeometryLiteral().lexicalForm()));
            doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_SOURCE_DATATYPE,
                    GeoConstants.GEO_WKT_LITERAL.stringValue()));
            doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_INDEX_CRS, IndexGeometry.INDEX_CRS));
            doc.add(new StoredField(LuceneGeoDocumentSchema.FIELD_INDEX_BUILD_MODE,
                    IndexGeometry.BUILD_MODE_TRANSFORMED_GEOMETRY));
            writer.addDocument(doc);
            markCurrentSchema(writer);
        }
    }

    private void writeCurrentSchemaIndexWithoutCommitMarker(Path dataDir) throws Exception {
        Path indexDir = GeoSparqlConfig.resolveIndexPath(dataDir);
        Files.createDirectories(indexDir);
        try (FSDirectory dir = FSDirectory.open(indexDir);
            IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
            writer.addDocument(currentSchemaDocument(1L, geometries.get(0)));
        }
    }

    private void writeMarkedCurrentSchemaIndex(Path dataDir) throws Exception {
        Path indexDir = GeoSparqlConfig.resolveIndexPath(dataDir);
        Files.createDirectories(indexDir);
        try (FSDirectory dir = FSDirectory.open(indexDir);
            IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
            writer.addDocument(currentSchemaDocument(1L, geometries.get(0)));
            markCurrentSchema(writer);
        }
    }

    private void writeEmptyIndexWithoutCommitMarker(Path dataDir) throws Exception {
        Path indexDir = GeoSparqlConfig.resolveIndexPath(dataDir);
        Files.createDirectories(indexDir);
        try (FSDirectory dir = FSDirectory.open(indexDir);
            IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
            writer.commit();
        }
    }

    private void markCurrentSchema(IndexWriter writer) {
        writer.setLiveCommitData(LuceneGeoDocumentSchema.currentSchemaCommitData(writer.getLiveCommitData()));
    }

    private IndexWriterConfig noMergeIndexWriterConfig() {
        IndexWriterConfig config = new IndexWriterConfig();
        config.setMergePolicy(NoMergePolicy.INSTANCE);
        return config;
    }

    private byte[] serializeGeometry(Geometry geometry) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(geometry);
        oos.close();
        return bos.toByteArray();
    }
}
