package com.ontotext.trree.geosparql.lucene;

import com.ontotext.trree.geosparql.CandidateLookupPolicy;
import com.ontotext.trree.geosparql.EntityGeometryIterator;
import com.ontotext.trree.geosparql.GeoSparqlConfig;
import com.ontotext.trree.geosparql.GeoSparqlIndexer;
import com.ontotext.trree.geosparql.GeoSparqlPlugin;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.sdk.PluginException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.composite.CompositeSpatialStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.shape.jts.JtsGeometry;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Lucene implementation of the GeoSPARQL indexer.
 */
public class LuceneGeoIndexer implements GeoSparqlIndexer {
	private GeoSparqlPlugin parent;

	private JtsSpatialContext ctx;

    private SpatialStrategy strategy;
    private Path indexDir;

    private Directory directory;

    private IndexWriterConfig iwConfig;
	private IndexWriter indexWriter;
    private Logger logger;
	private boolean schemaMismatchDetected;

	static final String SCHEMA_MISMATCH_MESSAGE = "Existing GeoSPARQL Lucene index does not match current schema v2. "
			+ "Jena-backed CRS-correct evaluation requires a full GeoSPARQL reindex. "
			+ "Queries are unavailable until reindex completes; run the documented force-reindex control or command.";

	public LuceneGeoIndexer(GeoSparqlPlugin parent) {
		this.parent = parent;
	}


	@Override
	public void initialize() throws Exception {
		this.logger = parent.getLogger();

		this.ctx = JtsSpatialContext.GEO;

        this.indexDir = GeoSparqlConfig.resolveIndexPath(parent.getDataDir().toPath());

		this.directory = FSDirectory.open(indexDir);

		initSettings();
		schemaMismatchDetected = detectSchemaMismatch();
	}

	@Override
	public void initSettings() {
		SpatialPrefixTree grid;
		GeoSparqlConfig.PrefixTree prefixTree = parent.getConfig().getCurrentPrefixTree();
		int precision = parent.getConfig().getCurrentPrecision();
		if (prefixTree == GeoSparqlConfig.PrefixTree.QUAD) {
			grid = new QuadPrefixTree(ctx, precision);
		} else if (prefixTree == GeoSparqlConfig.PrefixTree.GEOHASH) {
			grid = new GeohashPrefixTree(ctx, precision);
		} else {
			throw new PluginException("Unexpected prefix tree type: " + prefixTree);
		}

		RecursivePrefixTreeStrategy rptStrategy = new RecursivePrefixTreeStrategy(grid,
				LuceneGeoDocumentSchema.FIELD_SPATIAL_PREFIX);
		SerializedDVStrategy sdvStrategy = new SerializedDVStrategy(ctx,
				LuceneGeoDocumentSchema.FIELD_SPATIAL_DV);
		this.strategy = new CompositeSpatialStrategy(LuceneGeoDocumentSchema.FIELD_INDEX_GEOMETRY,
				rptStrategy, sdvStrategy);
	}

	@Override
	public void begin() throws Exception {
		iwConfig = new IndexWriterConfig();
		// Turn off compound file format.
		// Building the compound file format takes time during indexing (7-33%)
		iwConfig.setUseCompoundFile(false);
		// Set maxBufferedDocs large enough to prevent the writer from flushing based on document count.
		iwConfig.setMaxBufferedDocs(parent.getConfig().getMaxBufferedDocs());
		//More RAM before flushing means Lucene writes larger segments to begin with which means less merging later.
		iwConfig.setRAMBufferSizeMB(parent.getConfig().getRamBufferSizeMb());
		indexWriter = new IndexWriter(directory, iwConfig);
	}

	@Override
	public void commit() throws Exception {
		indexWriter.close(); // also commits
	}

	@Override
	public void rollback() throws Exception {
		indexWriter.rollback();
	}

	@Override
	public void freshIndex() throws Exception {
		indexWriter.deleteAll();
		schemaMismatchDetected = false;
	}

	@Override
	public void indexGeometryList(long subject, Function<Long, String> subjectMapper, List<IndexGeometry> geometries) {
		//logger.info("Indexing literal for {}; {}", parent.getEntities().get(subject), geometries.size());
		try {
			indexWriter.deleteDocuments(LuceneGeoDocumentSchema.entityIdQuery(subject));
			if (!geometries.isEmpty()) {
				for (IndexGeometry geometry : geometries) {
					indexWriter.addDocument(LuceneGeoDocumentSchema.toDocument(subject, geometry, strategy, ctx));
				}
			}
		} catch (Exception e) {
			handleCreateDocumentUnhandledException(subject, subjectMapper, e);
		}
	}

	@Override
	public void indexGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry) {
		try {
			indexWriter.addDocument(LuceneGeoDocumentSchema.toDocument(subject, geometry, strategy, ctx));
		} catch (Exception e) {
			handleCreateDocumentUnhandledException(subject, subjectMapper, e);
		}
	}

	@Override
	public EntityGeometryIterator getMatchingObjects(Geometry geometry, CandidateLookupPolicy candidateLookupPolicy) {
		assertCurrentSchema();
		if (candidateLookupPolicy == CandidateLookupPolicy.FULL_SCAN) {
			return getGeometriesFor(0);
		} else if (candidateLookupPolicy == CandidateLookupPolicy.INTERSECTS) {
			JtsGeometry shape = new JtsGeometry(geometry, ctx, true, true);
			// Adds an index to JtsGeometry class internally to compute spatial relations faster.
			shape.index();
			final SpatialArgs args = new SpatialArgs(SpatialOperation.Intersects, shape);

			Query query = strategy.makeQuery(args);

			return getIteratorForQuery(query);
		}
		throw new PluginException("Unsupported GeoSPARQL candidate lookup policy: " + candidateLookupPolicy);
	}

	@Override
	public EntityGeometryIterator getGeometriesFor(long subject) {
		assertCurrentSchema();
		final Query query;
		if (subject > 0) {
			query = LuceneGeoDocumentSchema.entityIdQuery(subject);
		} else {
			query = LuceneGeoDocumentSchema.allDocumentsQuery();
		}

		return getIteratorForQuery(query);
	}

	private EntityGeometryIterator getIteratorForQuery(Query query) {
		IndexReader indexReader = null;
		try {
			indexReader = DirectoryReader.open(directory);
			IndexSearcher indexSearcher = new IndexSearcher(indexReader);

			return new LuceneEntityGeometryIterator(indexSearcher, query);
		} catch (Exception e) {
			if (indexReader != null) {
				try {
					indexReader.close();
				} catch (IOException x) {
					// ignore
				}
			}
			throw new PluginException("Unable to execute Lucene query.", e);
		}

	}

	private void handleCreateDocumentUnhandledException(long subject, Function<Long, String> subjectMapper, Exception e) {
		String subjectIri = subjectMapper.apply(subject);

		if (parent.getConfig().isIgnoreErrors()) {
			logger.warn("Could not create GeoDocument for subject " + subjectIri, e);
		} else {
			throw new PluginException("Could not create GeoDocument for subject " + subjectIri +
					"\nIf you want to ignore this message and still build the index configure ignoreErrors = true (refer to documentation) and rebuild the index", e);
		}
	}

	private void assertCurrentSchema() {
		if (schemaMismatchDetected) {
			throw new PluginException(SCHEMA_MISMATCH_MESSAGE);
		}
	}

	private boolean detectSchemaMismatch() {
		try {
			if (!DirectoryReader.indexExists(directory)) {
				return false;
			}
			try (IndexReader reader = DirectoryReader.open(directory)) {
				for (int i = 0; i < reader.maxDoc(); i++) {
					Document doc = reader.document(i);
					if (!LuceneGeoDocumentSchema.isCurrentSchemaDocument(doc)) {
						return true;
					}
				}
			}
			return false;
		} catch (IndexNotFoundException e) {
			return false;
		} catch (IOException e) {
			throw new PluginException("Unable to inspect GeoSPARQL Lucene index schema.", e);
		}
	}

}
