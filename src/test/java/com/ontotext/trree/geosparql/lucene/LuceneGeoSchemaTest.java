package com.ontotext.trree.geosparql.lucene;

import com.ontotext.test.TemporaryLocalFolder;
import com.ontotext.trree.geosparql.CandidateEntity;
import com.ontotext.trree.geosparql.CloseableIterator;
import com.ontotext.trree.geosparql.GeoSparqlConfig;
import com.ontotext.trree.geosparql.GeoSparqlPlugin;
import com.ontotext.trree.geosparql.TestIndexGeometries;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import com.ontotext.trree.sdk.PluginException;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.composite.CompositeSpatialStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.context.jts.JtsSpatialContextFactory;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LuceneGeoSchemaTest {
	private static final Logger LOG = LoggerFactory.getLogger(LuceneGeoSchemaTest.class);
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String PROJECTED_POINT_WKT =
			"<" + EPSG_32634 + "> POINT(799997.80 4589779.63)";

	@Rule
	public TemporaryLocalFolder tmpFolder = new TemporaryLocalFolder();

	private IndexGeometry sampleGeometry;
	private JtsSpatialContext testSpatialContext;
	private SpatialStrategy testCompositeStrategy;

	@Before
	public void init() {
		JtsSpatialContextFactory contextFactory = new JtsSpatialContextFactory();
		contextFactory.geo = true;
		contextFactory.binaryCodecClass = JtsBinaryCodec.class;
		testSpatialContext = contextFactory.newSpatialContext();
		RecursivePrefixTreeStrategy prefixStrategy = new RecursivePrefixTreeStrategy(
				new QuadPrefixTree(testSpatialContext, 11), LuceneGeoDocumentSchema.FIELD_SPATIAL_PREFIX);
		SerializedDVStrategy geometryStrategy = new SerializedDVStrategy(testSpatialContext,
				LuceneGeoDocumentSchema.FIELD_INDEX_GEOMETRY);
		testCompositeStrategy = new CompositeSpatialStrategy("geoData", prefixStrategy, geometryStrategy);
		sampleGeometry = TestIndexGeometries.fromWkt("POINT(1 2)");
	}

	private LuceneGeoIndexer createIndexer(File dataDir) throws Exception {
		GeoSparqlPlugin parent = new GeoSparqlPlugin();
		parent.setConfig(new GeoSparqlConfig());
		parent.setLogger(LOG);
		parent.setDataDir(dataDir);

		LuceneGeoIndexer indexer = new LuceneGeoIndexer(parent);
		indexer.initialize();
		return indexer;
	}

	private FailingCommitLuceneGeoIndexer createFailingCommitIndexer(File dataDir) throws Exception {
		GeoSparqlPlugin parent = new GeoSparqlPlugin();
		parent.setConfig(new GeoSparqlConfig());
		parent.setLogger(LOG);
		parent.setDataDir(dataDir);

		FailingCommitLuceneGeoIndexer indexer = new FailingCommitLuceneGeoIndexer(parent);
		indexer.initialize();
		return indexer;
	}

	private Document currentSchemaDocument(long entityId, IndexGeometry geometry) {
		return LuceneGeoDocumentSchema.toDocument(entityId, geometry,
				testCompositeStrategy, testSpatialContext);
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
        CloseableIterator<IndexGeometry> iterator = indexer.getGeometriesFor(0);

        try {
            PluginException exception = assertThrows(PluginException.class, iterator::next);

            assertForceReindexMessage(exception);
        } finally {
            iterator.close();
        }
    }

    @Test
    public void testMarkedIndexMissingIndexGeometryDocValuesRequiresForceReindex() throws Exception {
        Path malformedDataDir = tmpFolder.getRoot().toPath().resolve("missing-index-doc-values");
        Files.createDirectories(malformedDataDir);
        Document document = currentSchemaDocument(1L, sampleGeometry);
        document.removeFields(LuceneGeoDocumentSchema.FIELD_INDEX_GEOMETRY);
        writeMarkedDocument(malformedDataDir, document);

        LuceneGeoIndexer indexer = createIndexer(malformedDataDir.toFile());
        CloseableIterator<IndexGeometry> iterator = indexer.getGeometriesFor(1L);
        try {
            assertForceReindexMessage(assertThrows(PluginException.class, iterator::next));
        } finally {
            iterator.close();
        }
    }

	@Test
	public void testMarkedIndexWithMalformedIndexGeometryDocValuesRequiresForceReindex() throws Exception {
		Path malformedDataDir = tmpFolder.getRoot().toPath().resolve("malformed-index-doc-values");
		Files.createDirectories(malformedDataDir);
		Document document = currentSchemaDocument(1L, sampleGeometry);
		document.removeFields(LuceneGeoDocumentSchema.FIELD_INDEX_GEOMETRY);
		document.add(new BinaryDocValuesField(LuceneGeoDocumentSchema.FIELD_INDEX_GEOMETRY,
				new BytesRef(new byte[]{Byte.MAX_VALUE})));
		writeMarkedDocument(malformedDataDir, document);

		LuceneGeoIndexer indexer = createIndexer(malformedDataDir.toFile());
		CloseableIterator<IndexGeometry> iterator = indexer.getGeometriesFor(1L);
		try {
			assertForceReindexMessage(assertThrows(PluginException.class, iterator::next));
		} finally {
			iterator.close();
		}
	}

    @Test
    public void testMalformedSourceWkbRequiresForceReindex() throws Exception {
        Path malformedDataDir = tmpFolder.getRoot().toPath().resolve("malformed-source-wkb");
        Files.createDirectories(malformedDataDir);
        Document document = currentSchemaDocument(1L, sampleGeometry);
        document.removeFields(LuceneGeoDocumentSchema.FIELD_SOURCE_GEOMETRY);
        document.add(new StoredField(LuceneGeoDocumentSchema.FIELD_SOURCE_GEOMETRY, new byte[]{1, 2, 3}));
        writeMarkedDocument(malformedDataDir, document);

        LuceneGeoIndexer indexer = createIndexer(malformedDataDir.toFile());
        CloseableIterator<CandidateEntity> iterator = indexer.getAllEntities();
        try {
            assertForceReindexMessage(assertThrows(PluginException.class, iterator::hasNext));
        } finally {
            iterator.close();
        }
    }

    @Test
    public void testConflictingValidatedSourceCachesRequireForceReindexInBothRetrievalPaths() throws Exception {
        Path malformedDataDir = tmpFolder.getRoot().toPath().resolve("conflicting-source-caches");
        Files.createDirectories(malformedDataDir);
        SourceGeometryLiteral firstSource = SourceGeometryLiteral.fromWkt("POINT(1 2)");
        SourceGeometryLiteral otherGeometry = SourceGeometryLiteral.fromWkt("POINT(3 4)");
        SourceGeometryLiteral conflictingSource = SourceGeometryLiteral.fromStoredGeometry(
                firstSource.lexicalForm(), firstSource.datatype(), firstSource.effectiveCrsUri(),
                otherGeometry.asGeometryWrapper().getXYGeometry().copy(),
                otherGeometry.asGeometryWrapper().getDimensionInfo());
        IndexGeometry first = IndexGeometry.fromStoredMetadata(
                firstSource.asGeometryWrapper().getXYGeometry().copy(), firstSource,
                firstSource.effectiveCrsUri(), IndexGeometry.INDEX_CRS,
                IndexGeometry.BUILD_MODE_TRANSFORMED_GEOMETRY);
        IndexGeometry conflicting = IndexGeometry.fromStoredMetadata(
                conflictingSource.asGeometryWrapper().getXYGeometry().copy(), conflictingSource,
                conflictingSource.effectiveCrsUri(), IndexGeometry.INDEX_CRS,
                IndexGeometry.BUILD_MODE_TRANSFORMED_GEOMETRY);
        writeMarkedDocuments(malformedDataDir, List.of(
                currentSchemaDocument(1L, first), currentSchemaDocument(1L, conflicting)));

        LuceneGeoIndexer indexer = createIndexer(malformedDataDir.toFile());
        CloseableIterator<CandidateEntity> iterator = indexer.getAllEntities();
        try {
            assertForceReindexMessage(assertThrows(PluginException.class, iterator::hasNext));
        } finally {
            iterator.close();
        }

        CloseableIterator<IndexGeometry> boundIterator = indexer.getGeometriesFor(1L);
        try {
            assertTrue(boundIterator.hasNext());
            boundIterator.next();
            assertForceReindexMessage(assertThrows(PluginException.class, boundIterator::next));
        } finally {
            boundIterator.close();
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
    public void testDevelopmentV2IndexMissingFinalLayoutMarkerRequiresReindex() throws Exception {
        Path developmentV2DataDir = tmpFolder.getRoot().toPath().resolve("development-v2-marker");
        Files.createDirectories(developmentV2DataDir);
        writeDevelopmentV2IndexWithoutLayoutMarker(developmentV2DataDir);

        LuceneGeoIndexer indexer = createIndexer(developmentV2DataDir.toFile());

        PluginException exception = assertThrows(PluginException.class, () -> indexer.getGeometriesFor(0));

        assertForceReindexMessage(exception);
    }

    @Test
    public void testDevelopmentV2IndexWithStoredOnlyBuildModeLayoutRequiresReindex() throws Exception {
        Path developmentV2DataDir = tmpFolder.getRoot().toPath().resolve("development-v2-stored-build-mode");
        Files.createDirectories(developmentV2DataDir);
        writeDevelopmentV2IndexWithStoredOnlyBuildModeLayout(developmentV2DataDir);

        LuceneGeoIndexer indexer = createIndexer(developmentV2DataDir.toFile());

        PluginException exception = assertThrows(PluginException.class, () -> indexer.getGeometriesFor(0));

        assertForceReindexMessage(exception);
    }

    @Test
    public void testMarkedCurrentSchemaV2IndexPassesStartupGate() throws Exception {
        Path markedDataDir = tmpFolder.getRoot().toPath().resolve("marked-schema-v2");
        Files.createDirectories(markedDataDir);
        writeMarkedCurrentSchemaIndex(markedDataDir);

        LuceneGeoIndexer indexer = createIndexer(markedDataDir.toFile());
        CloseableIterator<IndexGeometry> iterator = indexer.getGeometriesFor(0);

        try {
            assertTrue(iterator.hasNext());
            IndexGeometry geometry = iterator.next();
            assertNotNull(geometry.indexGeometry());
            assertNotNull(geometry.sourceGeometryLiteral());
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
        indexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(sampleGeometry));
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
            writer.addDocument(currentSchemaDocument(1L, sampleGeometry));
            writer.commit();
            writer.addDocument(currentSchemaDocument(2L, sampleGeometry));
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
                    indexer.indexGeometryList(2L, subject -> "Subject " + subject, List.of(sampleGeometry)));

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
            indexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(sampleGeometry));

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
        indexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(sampleGeometry));
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
        indexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(sampleGeometry));
        indexer.commit();

        CloseableIterator<IndexGeometry> iterator = indexer.getGeometriesFor(0);
        try {
            assertTrue(iterator.hasNext());
            IndexGeometry geometry = iterator.next();
            assertNotNull(geometry.indexGeometry());
            assertNotNull(geometry.sourceGeometryLiteral());
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
        IndexGeometry projectedGeometry = TestIndexGeometries.fromSource(
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

        CloseableIterator<IndexGeometry> storedGeometries = projectedIndexer.getGeometriesFor(1L);
        try {
            assertTrue(storedGeometries.hasNext());
            IndexGeometry storedGeometry = storedGeometries.next();
            assertEquals(PROJECTED_POINT_WKT, storedGeometry.sourceGeometryLiteral().lexicalForm());
            assertEquals(GeoConstants.GEO_WKT_LITERAL, storedGeometry.sourceGeometryLiteral().datatype());
            assertEquals(EPSG_32634, storedGeometry.sourceGeometryLiteral().effectiveCrsUri());
            assertEquals(IndexGeometry.INDEX_CRS, storedGeometry.indexCrs());
            assertFalse(storedGeometries.hasNext());
        } finally {
            storedGeometries.close();
        }
    }

    private void assertForceReindexMessage(PluginException exception) {
        assertTrue(exception.getMessage().contains("current schema v2"));
        assertTrue(exception.getMessage().contains("force-reindex"));
    }

    private void assertCurrentSchemaCommitData(IndexReader reader) throws IOException {
        assertEquals(LuceneGeoDocumentSchema.COMMIT_SCHEMA_VERSION_VALUE,
                ((DirectoryReader) reader).getIndexCommit().getUserData()
                        .get(LuceneGeoDocumentSchema.COMMIT_SCHEMA_VERSION_KEY));
        assertEquals(LuceneGeoDocumentSchema.COMMIT_SCHEMA_LAYOUT_VALUE,
                ((DirectoryReader) reader).getIndexCommit().getUserData()
                        .get(LuceneGeoDocumentSchema.COMMIT_SCHEMA_LAYOUT_KEY));
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
                    serializeGeometry(sampleGeometry.indexGeometry())));
            writer.addDocument(doc);
        }
    }

    private void writeMarkedIndexMissingRequiredSchemaV2Field(Path dataDir) throws Exception {
        Path indexDir = GeoSparqlConfig.resolveIndexPath(dataDir);
        Files.createDirectories(indexDir);
        IndexGeometry geometry = sampleGeometry;
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
            writer.addDocument(currentSchemaDocument(1L, sampleGeometry));
        }
    }

    private void writeMarkedCurrentSchemaIndex(Path dataDir) throws Exception {
        Path indexDir = GeoSparqlConfig.resolveIndexPath(dataDir);
        Files.createDirectories(indexDir);
        try (FSDirectory dir = FSDirectory.open(indexDir);
            IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
            writer.addDocument(currentSchemaDocument(1L, sampleGeometry));
            markCurrentSchema(writer);
        }
    }

    private void writeDevelopmentV2IndexWithoutLayoutMarker(Path dataDir) throws Exception {
        Path indexDir = GeoSparqlConfig.resolveIndexPath(dataDir);
        Files.createDirectories(indexDir);
        try (FSDirectory dir = FSDirectory.open(indexDir);
            IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
            writer.addDocument(currentSchemaDocument(1L, sampleGeometry));
            writer.setLiveCommitData(Collections.singletonMap(
                    LuceneGeoDocumentSchema.COMMIT_SCHEMA_VERSION_KEY,
                    LuceneGeoDocumentSchema.COMMIT_SCHEMA_VERSION_VALUE).entrySet());
        }
    }

    private void writeDevelopmentV2IndexWithStoredOnlyBuildModeLayout(Path dataDir) throws Exception {
        Path indexDir = GeoSparqlConfig.resolveIndexPath(dataDir);
        Files.createDirectories(indexDir);
        try (FSDirectory dir = FSDirectory.open(indexDir);
             IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
            Document document = currentSchemaDocument(1L, sampleGeometry);
            document.removeFields(LuceneGeoDocumentSchema.FIELD_INDEX_BUILD_MODE);
            document.add(new StoredField(LuceneGeoDocumentSchema.FIELD_INDEX_BUILD_MODE,
                    IndexGeometry.BUILD_MODE_TRANSFORMED_GEOMETRY));
            writer.addDocument(document);
            Map<String, String> commitData = new HashMap<>();
            commitData.put(LuceneGeoDocumentSchema.COMMIT_SCHEMA_VERSION_KEY,
                    LuceneGeoDocumentSchema.COMMIT_SCHEMA_VERSION_VALUE);
            commitData.put(LuceneGeoDocumentSchema.COMMIT_SCHEMA_LAYOUT_KEY, "doc-values-source-wkb");
            writer.setLiveCommitData(commitData.entrySet());
        }
    }

    private void writeMarkedDocument(Path dataDir, Document document) throws Exception {
        writeMarkedDocuments(dataDir, List.of(document));
    }

    private void writeMarkedDocuments(Path dataDir, List<Document> documents) throws Exception {
        Path indexDir = GeoSparqlConfig.resolveIndexPath(dataDir);
        Files.createDirectories(indexDir);
        try (FSDirectory dir = FSDirectory.open(indexDir);
             IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
            writer.addDocuments(documents);
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
}
