package com.ontotext.trree.geosparql.lucene;

import com.ontotext.test.TemporaryLocalFolder;
import com.ontotext.trree.geosparql.GeoSparqlConfig;
import com.ontotext.trree.geosparql.EntityGeometryIterator;
import com.ontotext.trree.geosparql.GeoSparqlPlugin;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.sdk.PluginException;
import com.useekm.indexing.GeoConstants;
import com.useekm.types.GeoConvert;
import com.useekm.types.exception.InvalidGeometryException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.locationtech.jts.geom.Geometry;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
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

    private void wkt2Geometry() throws IOException, InvalidGeometryException {
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
    public void testDocumentsStoreSchemaV2Metadata() throws Exception {
        TopDocs docs = indexSearcher.search(new MatchAllDocsQuery(), 1);
        Document doc = indexReader.document(docs.scoreDocs[0].doc);

        assertEquals(IndexGeometry.SCHEMA_VERSION,
                doc.getField(IndexGeometry.FIELD_SCHEMA_VERSION).numericValue().intValue());
        assertNotNull(doc.get(IndexGeometry.FIELD_SOURCE_LEXICAL_FORM));
        assertEquals(GeoConstants.GEO_WKT_LITERAL.stringValue(), doc.get(IndexGeometry.FIELD_SOURCE_DATATYPE));
        assertEquals(IndexGeometry.INDEX_CRS, doc.get(IndexGeometry.FIELD_INDEX_CRS));
        assertEquals(IndexGeometry.BUILD_MODE_TRANSFORMED_GEOMETRY, doc.get(IndexGeometry.FIELD_INDEX_BUILD_MODE));
    }

    @Test
    public void testNullSpatialOperationScansAllDocuments() throws Exception {
        EntityGeometryIterator iterator = luceneGeoIndexer.getMatchingObjects(geometries.get(0).indexGeometry(), null);

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
    public void testLegacyIndexFailsFastWithActionableMessage() throws Exception {
        Path legacyDataDir = tmpFolder.getRoot().toPath().resolve("legacy");
        Files.createDirectories(legacyDataDir);
        writeLegacyIndex(legacyDataDir);

        LuceneGeoIndexer legacyIndexer = createIndexer(legacyDataDir.toFile());

        PluginException exception = assertThrows(PluginException.class, () -> legacyIndexer.getGeometriesFor(0));

        assertTrue(exception.getMessage().contains("schema v2 source geometry literal metadata"));
        assertTrue(exception.getMessage().contains("force-reindex"));
    }

    private long getDocId(ScoreDoc docs) throws IOException {
        return indexReader.document(docs.doc).getField("id").numericValue().longValue();
    }

    private void writeLegacyIndex(Path dataDir) throws Exception {
        Path indexDir = GeoSparqlConfig.resolveIndexPath(dataDir);
        Files.createDirectories(indexDir);
        try (FSDirectory dir = FSDirectory.open(indexDir);
             IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
            Document doc = new Document();
            doc.add(new LongPoint("id", 1L));
            doc.add(new StoredField("id", 1L));
            doc.add(new StoredField("geoData", serializeGeometry(geometries.get(0).indexGeometry())));
            writer.addDocument(doc);
        }
    }

    private byte[] serializeGeometry(Geometry geometry) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(geometry);
        oos.close();
        return bos.toByteArray();
    }
}
