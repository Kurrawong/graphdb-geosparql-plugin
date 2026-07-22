package com.ontotext.trree.geosparql.lucene;

import com.ontotext.test.TemporaryLocalFolder;
import com.ontotext.trree.geosparql.CandidateLookupPolicy;
import com.ontotext.trree.geosparql.CandidateEntity;
import com.ontotext.trree.geosparql.CloseableIterator;
import com.ontotext.trree.geosparql.GeoSparqlConfig;
import com.ontotext.trree.geosparql.GeoSparqlPlugin;
import com.ontotext.trree.geosparql.TestIndexGeometries;
import com.ontotext.trree.geosparql.jena.JenaGeoSparqlException;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.sdk.PluginException;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.DocValuesFieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.composite.CompositeSpatialStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.context.jts.JtsSpatialContextFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
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

    private IndexGeometry sampleGeometry;

    private IndexSearcher indexSearcher;

    private IndexReader indexReader;

    private JtsSpatialContext testSpatialContext;

    private SerializedDVStrategy testGeometryStrategy;

    private SpatialStrategy testCompositeStrategy;

    private Map<Long, Long> expected = new HashMap<>();

    @Before
    public void init() throws Exception {
        initializeTestSpatialStrategy();
		sampleGeometry = TestIndexGeometries.fromWkt("POINT(1 2)");
    }

    private void initializeExampleIndex() throws Exception {
		initIndexer();
		List<IndexGeometry> exampleGeometries = new ArrayList<>();
		readExampleGeometries(exampleGeometries);
		indexGeometries(exampleGeometries);

        FSDirectory dir = FSDirectory.open(GeoSparqlConfig.resolveIndexPath(tmpFolder.getRoot().toPath()));
        indexReader = DirectoryReader.open(dir);
        indexSearcher = new IndexSearcher(indexReader);
    }

    private void initializeTestSpatialStrategy() {
        JtsSpatialContextFactory contextFactory = new JtsSpatialContextFactory();
        contextFactory.geo = true;
        contextFactory.binaryCodecClass = JtsBinaryCodec.class;
        testSpatialContext = contextFactory.newSpatialContext();
        RecursivePrefixTreeStrategy prefixStrategy = new RecursivePrefixTreeStrategy(
                new QuadPrefixTree(testSpatialContext, 11), LuceneGeoDocumentSchema.FIELD_SPATIAL_PREFIX);
        testGeometryStrategy = new SerializedDVStrategy(testSpatialContext,
                LuceneGeoDocumentSchema.FIELD_INDEX_GEOMETRY);
        testCompositeStrategy = new CompositeSpatialStrategy("geoData", prefixStrategy, testGeometryStrategy);
    }

    private void indexGeometries(List<IndexGeometry> exampleGeometries) throws Exception {
        for (long i = 1, c = 1; i < exampleGeometries.size(); i++) {
            luceneGeoIndexer.indexGeometryList(i, (subject) -> "Subject " + subject, exampleGeometries);
            for (int k = 0; k < exampleGeometries.size(); k++) {
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


    private void readExampleGeometries(List<IndexGeometry> exampleGeometries) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                LuceneGeoIndexerTest.class.getResourceAsStream("/example_data.wkt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                exampleGeometries.add(TestIndexGeometries.fromSource(SourceGeometryLiteral.fromWkt(line)));
            }
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
		initializeExampleIndex();
        TopDocs docs = indexSearcher.search(new MatchAllDocsQuery(), 100);

        assertEquals(docs.scoreDocs.length, 56);

        for (int i = 1; i <= docs.scoreDocs.length; i++) {
            assertEquals((long) expected.get((long) i), getDocId(docs.scoreDocs[i - 1]));
        }
    }

    @Test
    public void testDocumentsStoreSchemaV2EffectiveSourceCrsMetadata() throws Exception {
		initializeExampleIndex();
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
        assertEquals(1, doc.getField(LuceneGeoDocumentSchema.FIELD_SOURCE_GEOMETRY)
                .binaryValue().bytes[doc.getField(LuceneGeoDocumentSchema.FIELD_SOURCE_GEOMETRY)
                .binaryValue().offset]);
        assertNotNull(doc.getField(LuceneGeoDocumentSchema.FIELD_SOURCE_COORDINATE_DIMENSION).numericValue());
        assertNotNull(doc.getField(LuceneGeoDocumentSchema.FIELD_SOURCE_SPATIAL_DIMENSION).numericValue());
        assertNotNull(doc.getField(LuceneGeoDocumentSchema.FIELD_SOURCE_TOPOLOGICAL_DIMENSION).numericValue());
        assertTrue(hasRequiredEntityIdDocValues(indexReader));
        for (LeafReaderContext leaf : indexReader.leaves()) {
            FieldInfo prefixField = leaf.reader().getFieldInfos().fieldInfo(
                    LuceneGeoDocumentSchema.FIELD_SPATIAL_PREFIX);
            FieldInfo indexGeometryField = leaf.reader().getFieldInfos().fieldInfo(
                    LuceneGeoDocumentSchema.FIELD_INDEX_GEOMETRY);
            assertNotNull(prefixField);
            assertTrue(prefixField.getIndexOptions() != IndexOptions.NONE);
            assertNotNull(indexGeometryField);
            assertEquals(DocValuesType.BINARY, indexGeometryField.getDocValuesType());
        }
        assertCurrentSchemaCommitData(indexReader);
    }

    @Test
    public void testFullScanCandidateLookupScansAllDocuments() throws Exception {
		initializeExampleIndex();
        CloseableIterator<IndexGeometry> iterator = luceneGeoIndexer.getGeometriesFor(0);

        int count = 0;
        try {
            while (iterator.hasNext()) {
                IndexGeometry geometry = iterator.next();
                assertNotNull(geometry.indexGeometry());
                assertNotNull(geometry.sourceGeometryLiteral());
                count++;
            }
        } finally {
            iterator.close();
        }

        assertEquals(56, count);
    }

    @Test
    public void repeatedSourceWritesOneDocumentAndReturnsOneCandidateSource() throws Exception {
        IndexGeometry geometry = TestIndexGeometries.fromWkt("POINT(0 0)");
        Path entityCandidateDataDir = tmpFolder.getRoot().toPath().resolve("entity-candidates");
        Files.createDirectories(entityCandidateDataDir);

        LuceneGeoIndexer entityCandidateIndexer = createIndexer(entityCandidateDataDir.toFile());
        entityCandidateIndexer.begin();
        entityCandidateIndexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(geometry, geometry));
        entityCandidateIndexer.commit();

		try (FSDirectory directory = FSDirectory.open(GeoSparqlConfig.resolveIndexPath(entityCandidateDataDir));
			 IndexReader reader = DirectoryReader.open(directory)) {
			assertEquals(1, reader.numDocs());
		}

		CloseableIterator<CandidateEntity> iterator = entityCandidateIndexer.getCandidatesForSource(geometry,
				CandidateLookupPolicy.EQUALS);
		try {
			assertTrue(iterator.hasNext());
			CandidateEntity candidate = iterator.next();
			assertEquals(1L, candidate.entityId());
			assertEquals(List.of(geometry.sourceGeometryLiteral()), candidate.matchingSourceGeometryLiterals());
			assertFalse(iterator.hasNext());
		} finally {
			iterator.close();
		}
    }

    @Test
	public void collectionEnvelopeGeneratesCandidatesAndEmptySentinelRemainsFullScanVisible() throws Exception {
		IndexGeometry collectionEnvelope = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION(POINT(0 0),POINT(10 10))"));
		IndexGeometry emptySentinel = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION EMPTY"));
        Path dataDir = tmpFolder.getRoot().toPath().resolve("collection-candidates");
        Files.createDirectories(dataDir);

        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
        indexer.begin();
		indexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(collectionEnvelope));
        indexer.indexGeometryList(2L, subject -> "Subject " + subject, List.of(emptySentinel));
        indexer.commit();

		assertArrayEquals(new long[]{1L}, collectEntityIds(indexer.getCandidatesForSource(
				collectionEnvelope, CandidateLookupPolicy.INTERSECTS)));
        assertArrayEquals(new long[]{1L, 2L}, collectEntityIds(indexer.getAllEntities()));

        try (FSDirectory directory = FSDirectory.open(GeoSparqlConfig.resolveIndexPath(dataDir));
             IndexReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            FieldInfo buildModeField = reader.leaves().get(0).reader().getFieldInfos()
                    .fieldInfo(LuceneGeoDocumentSchema.FIELD_INDEX_BUILD_MODE);
            assertNotNull(buildModeField);
            assertEquals(IndexOptions.DOCS, buildModeField.getIndexOptions());
			assertEquals(1, searcher.count(new PrefixQuery(new Term(
					LuceneGeoDocumentSchema.FIELD_SPATIAL_PREFIX, ""))));
			assertEquals(1, searcher.count(new DocValuesFieldExistsQuery(
					LuceneGeoDocumentSchema.FIELD_INDEX_GEOMETRY)));
			assertEquals(1, searcher.count(new TermQuery(new Term(
					LuceneGeoDocumentSchema.FIELD_INDEX_BUILD_MODE,
					IndexGeometry.BUILD_MODE_TRANSFORMED_ENVELOPE))));
            assertEquals(1, searcher.count(new TermQuery(new Term(
                    LuceneGeoDocumentSchema.FIELD_INDEX_BUILD_MODE,
                    IndexGeometry.BUILD_MODE_EMPTY_SENTINEL))));
			assertEquals(2, reader.numDocs());
        }

        CloseableIterator<IndexGeometry> iterator = indexer.getGeometriesFor(2L);
        try {
            assertTrue(iterator.hasNext());
            IndexGeometry sentinel = iterator.next();
            assertTrue(sentinel.indexGeometry().isEmpty());
            assertEquals(IndexGeometry.BUILD_MODE_EMPTY_SENTINEL,
                    sentinel.indexBuildMode());
            assertFalse(sentinel.isSpatialCandidate());
            assertFalse(iterator.hasNext());
        } finally {
            iterator.close();
        }
    }


    @Test
	public void fullScanReturnsCompleteSourceFromCollectionEnvelopeDocument() throws Exception {
		IndexGeometry collectionEnvelope = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION(POINT(0 0),LINESTRING(0 0,1 1))"));
        Path dataDir = tmpFolder.getRoot().toPath().resolve("collection-source-reuse-entity-ids");
        Files.createDirectories(dataDir);

        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
        indexer.begin();
		indexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(collectionEnvelope));
        indexer.commit();

        CloseableIterator<CandidateEntity> entities = indexer.getAllEntities();
        try {
            assertTrue(entities.hasNext());
            CandidateEntity candidate = entities.next();
            assertEquals(1L, candidate.entityId());
            assertEquals(1, candidate.matchingSourceGeometryLiterals().size());
			assertEquals(collectionEnvelope.sourceGeometryLiteral(),
					candidate.matchingSourceGeometryLiterals().get(0));
            assertFalse(entities.hasNext());
        } finally {
            entities.close();
        }
    }

    @Test
	public void entityGeometryLookupReturnsOneCollectionEnvelope() throws Exception {
		IndexGeometry collectionEnvelope = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION(POINT(0 0),LINESTRING(0 0,1 1))"));
        Path dataDir = tmpFolder.getRoot().toPath().resolve("collection-source-reuse-geometry-lookup");
        Files.createDirectories(dataDir);

        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
        indexer.begin();
		indexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(collectionEnvelope));
        indexer.commit();

		CloseableIterator<IndexGeometry> geometries = indexer.getGeometriesFor(1L);
		try {
			assertTrue(geometries.hasNext());
			IndexGeometry storedEnvelope = geometries.next();
			assertEquals(IndexGeometry.BUILD_MODE_TRANSFORMED_ENVELOPE, storedEnvelope.indexBuildMode());
			assertEquals(collectionEnvelope.indexGeometry(), storedEnvelope.indexGeometry());
			assertFalse(geometries.hasNext());
		} finally {
			geometries.close();
		}
    }

    @Test
    public void entityTraversalRetainsDistinctSourceGeometryLiterals() throws Exception {
        IndexGeometry first = TestIndexGeometries.fromWkt("POINT(0 0)");
        IndexGeometry second = TestIndexGeometries.fromWkt("POINT(1 1)");
        Path dataDir = tmpFolder.getRoot().toPath().resolve("distinct-entity-sources");
        Files.createDirectories(dataDir);

        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
        indexer.begin();
        indexer.indexGeometryList(1L, subject -> "Subject " + subject, List.of(first, second));
        indexer.commit();

        CloseableIterator<IndexGeometry> geometries = indexer.getGeometriesFor(1L);
        try {
            assertTrue(geometries.hasNext());
            SourceGeometryLiteral firstSource = geometries.next().sourceGeometryLiteral();
            assertTrue(geometries.hasNext());
            SourceGeometryLiteral secondSource = geometries.next().sourceGeometryLiteral();
            assertNotSame(firstSource, secondSource);
            assertEquals("POINT(0 0)", firstSource.lexicalForm());
            assertEquals("POINT(1 1)", secondSource.lexicalForm());
            assertFalse(geometries.hasNext());
        } finally {
            geometries.close();
        }
    }

    @Test
    public void indexGeometryIteratorDiscardsSourceReuseAtEntityBoundary() throws Exception {
        IndexGeometry geometry = TestIndexGeometries.fromWkt("POINT(0 0)");
        try (Directory directory = new ByteBuffersDirectory();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            writer.addDocument(currentSchemaDocument(1L, geometry));
            writer.addDocument(currentSchemaDocument(2L, geometry));
            writer.commit();

            IndexReader reader = DirectoryReader.open(directory);
            LuceneIndexGeometryIterator iterator = indexGeometryIterator(new IndexSearcher(reader),
                    new MatchAllDocsQuery());
            try {
                SourceGeometryLiteral first = iterator.next().sourceGeometryLiteral();
                SourceGeometryLiteral second = iterator.next().sourceGeometryLiteral();
                assertNotSame(first, second);
                assertFalse(iterator.hasNext());
            } finally {
                iterator.close();
            }
        }
    }

	@Test
	public void indexGeometryReadPreservesUnsupportedCrsDeploymentFailure() {
		JenaGeoSparqlException unsupportedCrs = new JenaGeoSparqlException(
				"Unsupported CRS. Configure Apache SIS CRS data.", null, true);

		RuntimeException classified = LuceneIndexGeometryIterator.classifyReadFailure(unsupportedCrs);

		assertSame(unsupportedCrs, classified);
		assertTrue(((JenaGeoSparqlException) classified).isUnsupportedCrs());
		assertTrue(classified.getMessage().contains("Apache SIS"));
	}

	@Test
	public void indexGeometryReadTreatsOtherJenaFailuresAsSchemaMismatch() {
		JenaGeoSparqlException invalidStoredGeometry = new JenaGeoSparqlException(
				"Invalid stored source geometry");

		PluginException classified = (PluginException) LuceneIndexGeometryIterator.classifyReadFailure(
				invalidStoredGeometry);

		assertForceReindexMessage(classified);
		assertSame(invalidStoredGeometry, classified.getCause());
	}

	@Test
	public void indexGeometryReadTreatsExplicitCorruptionAndTruncationAsSchemaMismatch() {
		CorruptIndexException corruptIndex = new CorruptIndexException("Corrupt index", "test");
		EOFException truncatedData = new EOFException("Truncated binary data");

		PluginException corruptClassification = (PluginException)
				LuceneIndexGeometryIterator.classifyReadFailure(corruptIndex);
		PluginException truncatedClassification = (PluginException)
				LuceneIndexGeometryIterator.classifyReadFailure(truncatedData);

		assertForceReindexMessage(corruptClassification);
		assertSame(corruptIndex, corruptClassification.getCause());
		assertForceReindexMessage(truncatedClassification);
		assertSame(truncatedData, truncatedClassification.getCause());
	}

	@Test
	public void indexGeometryReadReportsOrdinaryIoFailureWithoutReindexGuidance() {
		IOException readFailure = new IOException("Filesystem read failed");

		PluginException classified = (PluginException) LuceneIndexGeometryIterator.classifyReadFailure(readFailure);

		assertEquals("Unable to read Lucene index geometry document.", classified.getMessage());
		assertFalse(classified.getMessage().contains("force-reindex"));
		assertFalse(classified.getMessage().contains("schema v2"));
		assertSame(readFailure, classified.getCause());
	}

	@Test
	public void indexGeometryReadPreservesUnrelatedRuntimeFailure() {
		AlreadyClosedException closedReader = new AlreadyClosedException("Reader is closed");

		RuntimeException classified = LuceneIndexGeometryIterator.classifyReadFailure(closedReader);

		assertSame(closedReader, classified);
	}

    @Test
	public void fullScanUsesCandidateReaderAndIsRejectedBySpatialLookup() throws Exception {
        IndexGeometry geometry = TestIndexGeometries.fromWkt("POINT(0 0)");
        Path entityCandidateDataDir = tmpFolder.getRoot().toPath().resolve("candidate-entity-geometries");
        Files.createDirectories(entityCandidateDataDir);

        CountingLuceneGeoIndexer entityCandidateIndexer = createCountingIndexer(entityCandidateDataDir.toFile());
        entityCandidateIndexer.begin();
        for (long subject = 1; subject <= 3; subject++) {
            entityCandidateIndexer.indexGeometryList(subject, id -> "Subject " + id, List.of(geometry));
        }
        entityCandidateIndexer.commit();

		PluginException sourceAwareException = assertThrows(PluginException.class, () ->
				entityCandidateIndexer.getCandidatesForSource(geometry, CandidateLookupPolicy.FULL_SCAN));
		assertTrue(sourceAwareException.getMessage().contains("getAllEntities"));

		CloseableIterator<CandidateEntity> iterator = entityCandidateIndexer.getAllEntities();

        try {
            for (int expectedEntityId = 1; expectedEntityId <= 3; expectedEntityId++) {
                assertTrue(iterator.hasNext());
                CandidateEntity candidate = iterator.next();
                assertEquals(expectedEntityId, candidate.entityId());
                assertEquals(1, candidate.matchingSourceGeometryLiterals().size());
            }
            assertEquals(1, entityCandidateIndexer.openReaderCount());
        } finally {
            iterator.close();
        }
    }

    @Test
    public void candidateEntityGeometryLookupDoesNotRunEntitySearchPerCandidate() throws Exception {
        IndexGeometry geometry = TestIndexGeometries.fromWkt("POINT(0 0)");
        try (Directory directory = new ByteBuffersDirectory();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            writer.addDocument(currentSchemaDocument(1L, geometry));
            writer.addDocument(currentSchemaDocument(2L, geometry));
            writer.addDocument(currentSchemaDocument(3L, geometry));
            writer.commit();

            try (IndexReader reader = DirectoryReader.open(directory)) {
                CountingIndexSearcher searcher = new CountingIndexSearcher(reader);
                CloseableIterator<CandidateEntity> iterator = new LuceneCandidateEntityIterator(searcher,
                        new MatchAllDocsQuery());

                try {
                    for (int expectedEntityId = 1; expectedEntityId <= 3; expectedEntityId++) {
                        assertTrue(iterator.hasNext());
                        CandidateEntity candidate = iterator.next();
                        assertEquals(expectedEntityId, candidate.entityId());
                        assertEquals(1, candidate.matchingSourceGeometryLiterals().size());
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
        IndexGeometry firstEntityGeometry1 = TestIndexGeometries.fromSource(
                SourceGeometryLiteral.fromWkt("POINT(0 0)"));
        IndexGeometry secondEntityGeometry = TestIndexGeometries.fromSource(
                SourceGeometryLiteral.fromWkt("POINT(1 1)"));
        IndexGeometry firstEntityGeometry2 = TestIndexGeometries.fromSource(
                SourceGeometryLiteral.fromWkt("POINT(2 2)"));

        try (Directory directory = new ByteBuffersDirectory();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            writer.addDocument(currentSchemaDocument(1L, firstEntityGeometry1));
            writer.addDocument(currentSchemaDocument(2L, secondEntityGeometry));
            writer.addDocument(currentSchemaDocument(1L, firstEntityGeometry2));
            writer.commit();

            try (IndexReader reader = DirectoryReader.open(directory)) {
                CloseableIterator<CandidateEntity> iterator = new LuceneCandidateEntityIterator(new IndexSearcher(reader),
                        new MatchAllDocsQuery());

                try {
                    assertTrue(iterator.hasNext());
                    CandidateEntity first = iterator.next();
                    assertEquals(1L, first.entityId());
                    assertEquals(2, first.matchingSourceGeometryLiterals().size());

                    assertTrue(iterator.hasNext());
                    CandidateEntity second = iterator.next();
                    assertEquals(2L, second.entityId());
                    assertEquals(1, second.matchingSourceGeometryLiterals().size());
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

    @Test
    public void indexGeometryIteratorReturnsValuesWithoutPriorHasNextAndThrowsWhenExhausted() throws Exception {
        IndexGeometry geometry = TestIndexGeometries.fromWkt("POINT(0 0)");
        try (Directory directory = new ByteBuffersDirectory();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            writer.addDocument(currentSchemaDocument(1L, geometry));
            writer.commit();

            IndexReader reader = DirectoryReader.open(directory);
            LuceneIndexGeometryIterator iterator = indexGeometryIterator(new IndexSearcher(reader),
                    new MatchAllDocsQuery());
            try {
                assertTrue(iterator.hasNext());
                assertTrue(iterator.hasNext());
                assertEquals("POINT(0 0)", iterator.next().sourceGeometryLiteral().lexicalForm());
                assertFalse(iterator.hasNext());
                assertThrows(NoSuchElementException.class, iterator::next);
            } finally {
                iterator.close();
                iterator.close();
            }
            assertEquals(0, reader.getRefCount());
        }
    }

    @Test
    public void candidateEntityIteratorGroupsValuesAcrossPageBoundary() throws Exception {
        IndexGeometry geometry = TestIndexGeometries.fromWkt("POINT(0 0)");
        try (Directory directory = new ByteBuffersDirectory();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            for (int i = 0; i < 1001; i++) {
                writer.addDocument(currentSchemaDocument(1L, geometry));
            }
            writer.commit();

            IndexReader reader = DirectoryReader.open(directory);
            LuceneCandidateEntityIterator iterator = new LuceneCandidateEntityIterator(new IndexSearcher(reader),
                    new MatchAllDocsQuery());
            try {
                CandidateEntity candidate = iterator.next();
                assertEquals(1L, candidate.entityId());
                assertEquals(1, candidate.matchingSourceGeometryLiterals().size());
                assertEquals("POINT(0 0)", candidate.matchingSourceGeometryLiterals().get(0).lexicalForm());
                assertFalse(iterator.hasNext());
                assertThrows(NoSuchElementException.class, iterator::next);
            } finally {
                iterator.close();
                iterator.close();
            }
            assertEquals(0, reader.getRefCount());
        }
    }

    private void assertFullScanReturnsDocumentCount(int documentCount) throws Exception {
        IndexGeometry geometry = TestIndexGeometries.fromWkt("POINT(0 0)");
        try (Directory directory = new ByteBuffersDirectory();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            for (long subject = 1; subject <= documentCount; subject++) {
                writer.addDocument(currentSchemaDocument(subject, geometry));
            }
            writer.commit();

            try (IndexReader reader = DirectoryReader.open(directory)) {
                CloseableIterator<IndexGeometry> iterator = indexGeometryIterator(new IndexSearcher(reader),
                        new MatchAllDocsQuery());

                int count = 0;
                try {
                    while (iterator.hasNext()) {
                        IndexGeometry indexGeometry = iterator.next();
                        assertNotNull(indexGeometry.indexGeometry());
                        assertNotNull(indexGeometry.sourceGeometryLiteral());
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
        IndexGeometry geometry = TestIndexGeometries.fromWkt("POINT(0 0)");
        try (Directory directory = new ByteBuffersDirectory();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            for (long subject = 1; subject <= documentCount; subject++) {
                writer.addDocument(currentSchemaDocument(subject, geometry));
            }
            writer.commit();

            try (IndexReader reader = DirectoryReader.open(directory)) {
                CloseableIterator<CandidateEntity> iterator = new LuceneCandidateEntityIterator(new IndexSearcher(reader),
                        new MatchAllDocsQuery());

                int count = 0;
                try {
                    while (iterator.hasNext()) {
                        assertEquals(count + 1, iterator.next().entityId());
                        count++;
                    }
                } finally {
                    iterator.close();
                }

                assertEquals(documentCount, count);
            }
        }
    }

    private long[] collectEntityIds(CloseableIterator<CandidateEntity> iterator) throws IOException {
        try {
            long[] ids = new long[16];
            int size = 0;
            while (iterator.hasNext()) {
                if (size == ids.length) {
                    long[] expanded = new long[ids.length * 2];
                    System.arraycopy(ids, 0, expanded, 0, ids.length);
                    ids = expanded;
                }
                ids[size++] = iterator.next().entityId();
            }

            long[] result = new long[size];
            System.arraycopy(ids, 0, result, 0, size);
            return result;
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


    private Document currentSchemaDocument(long entityId, IndexGeometry geometry) {
        return LuceneGeoDocumentSchema.toDocument(entityId, geometry,
                testCompositeStrategy, testSpatialContext);
    }

    private LuceneIndexGeometryIterator indexGeometryIterator(IndexSearcher searcher, Query query)
            throws IOException {
        return new LuceneIndexGeometryIterator(searcher, query, testGeometryStrategy, testSpatialContext);
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
            FieldInfo entityIdField = context.reader().getFieldInfos().fieldInfo(
                    LuceneGeoDocumentSchema.FIELD_ID);
            if (entityIdField == null || entityIdField.getDocValuesType() != DocValuesType.NUMERIC) {
                return false;
            }
        }
        return true;
    }


    private long getDocId(ScoreDoc docs) throws IOException {
        return indexReader.document(docs.doc).getField(LuceneGeoDocumentSchema.FIELD_ID).numericValue().longValue();
    }
}
