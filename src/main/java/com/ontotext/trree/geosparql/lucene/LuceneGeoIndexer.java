package com.ontotext.trree.geosparql.lucene;

import com.ontotext.trree.geosparql.CandidateLookupPolicy;
import com.ontotext.trree.geosparql.EntityGeometryIterator;
import com.ontotext.trree.geosparql.EntityIdIterator;
import com.ontotext.trree.geosparql.GeoSparqlConfig;
import com.ontotext.trree.geosparql.GeoSparqlIndexer;
import com.ontotext.trree.geosparql.GeoSparqlPlugin;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.sdk.PluginException;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.shape.jts.JtsGeometry;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Lucene-backed GeoSPARQL candidate indexer.
 *
 * <p>The Lucene index stores derived index geometry for coarse candidate lookup
 * and source geometry literal metadata for later CRS-aware exact evaluation.
 *
 * <p>During startup, the indexer performs only an index-level schema check. A
 * non-empty index must have the schema v2 commit marker; missing or mismatched
 * commit metadata causes query and update paths to fail with the force-reindex
 * message. Because this check reads commit metadata rather than stored
 * documents, startup remains independent of the number of indexed geometries.
 *
 * <p>Fresh indexes and empty indexes can accept v2 writes. Successful
 * v2 writes schedule the schema marker to be written on commit. Document-level
 * schema validation remains defensive: a marked index with malformed or mixed
 * documents fails during document decoding rather than silently using invalid
 * source geometry literal data.
 *
 * <p>During force reindex, writes are allowed after {@link #freshIndex()}, but
 * reads remain gated by the previously committed schema state until commit
 * succeeds. Rollback or failed commit restores that previous schema state, so an
 * abandoned reindex cannot make this indexer trust an old non-current index.
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
	private boolean schemaMarkerPending;
	private boolean schemaRebuildInProgress;
	private boolean schemaMismatchBeforeRebuild;

	static final String SCHEMA_MISMATCH_MESSAGE = LuceneGeoDocumentSchema.SCHEMA_MISMATCH_MESSAGE;

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
		// Lucene only generates coarse candidates. Exact relation evaluation is performed
		// against the source geometry literal, so serialized shape verification here is
		// both redundant and incompatible with the JTS 1.20 API required by RelateNG.
		this.strategy = rptStrategy;
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
		boolean rebuildWasInProgress = schemaRebuildInProgress;
		boolean committed = false;
		try {
			writeSchemaMarkerIfNeeded();
			closeIndexWriter(); // also commits
			committed = true;
			if (rebuildWasInProgress) {
				schemaMismatchDetected = false;
			}
		} finally {
			if (!committed && schemaRebuildInProgress) {
				schemaMismatchDetected = schemaMismatchBeforeRebuild;
			}
			schemaMarkerPending = false;
			schemaRebuildInProgress = false;
			schemaMismatchBeforeRebuild = false;
		}
	}

	void closeIndexWriter() throws IOException {
		indexWriter.close();
	}

	@Override
	public void rollback() throws Exception {
		try {
			indexWriter.rollback();
		} finally {
			restoreSchemaStateAfterUncommittedRebuild();
			schemaMarkerPending = false;
		}
	}

	@Override
	public void freshIndex() throws Exception {
		indexWriter.deleteAll();
		schemaMismatchBeforeRebuild = schemaMismatchDetected;
		schemaRebuildInProgress = true;
		schemaMarkerPending = true;
	}

	@Override
	public void indexGeometryList(long subject, Function<Long, String> subjectMapper, List<IndexGeometry> geometries) {
		//logger.info("Indexing literal for {}; {}", parent.getEntities().get(subject), geometries.size());
		assertWritableCurrentSchema();
		try {
			List<org.apache.lucene.document.Document> documents = new ArrayList<>(geometries.size());
			for (IndexGeometry geometry : geometries) {
				documents.add(LuceneGeoDocumentSchema.toDocument(subject, geometry, strategy, ctx));
			}
			indexWriter.deleteDocuments(LuceneGeoDocumentSchema.entityIdQuery(subject));
			if (!documents.isEmpty()) {
				indexWriter.addDocuments(documents);
				schemaMarkerPending = true;
			}
		} catch (Exception e) {
			handleCreateDocumentUnhandledException(subject, subjectMapper, e);
		}
	}

	@Override
	public void indexGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry) {
		assertWritableCurrentSchema();
		try {
			indexWriter.addDocument(LuceneGeoDocumentSchema.toDocument(subject, geometry, strategy, ctx));
			schemaMarkerPending = true;
		} catch (Exception e) {
			handleCreateDocumentUnhandledException(subject, subjectMapper, e);
		}
	}

	@Override
	public EntityGeometryIterator getMatchingObjects(Geometry geometry, CandidateLookupPolicy candidateLookupPolicy) {
		assertReadableCurrentSchema();
		return getIteratorForQuery(matchingObjectsQuery(geometry, candidateLookupPolicy));
	}

	@Override
	public EntityIdIterator getMatchingEntityIds(Geometry geometry, CandidateLookupPolicy candidateLookupPolicy) {
		assertReadableCurrentSchema();
		return getEntityIdsForQuery(matchingObjectsQuery(geometry, candidateLookupPolicy));
	}

	@Override
	public EntityIdIterator getAllEntityIds() {
		assertReadableCurrentSchema();
		return getEntityIdsForQuery(LuceneGeoDocumentSchema.allDocumentsQuery());
	}

	@Override
	public EntityGeometryIterator getGeometriesFor(long subject) {
		assertReadableCurrentSchema();
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
			indexReader = openReader();
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

	private Query matchingObjectsQuery(Geometry geometry, CandidateLookupPolicy candidateLookupPolicy) {
		if (candidateLookupPolicy == CandidateLookupPolicy.FULL_SCAN) {
			return LuceneGeoDocumentSchema.allDocumentsQuery();
		} else if (candidateLookupPolicy == CandidateLookupPolicy.INTERSECTS) {
			JtsGeometry shape = new JtsGeometry(geometry, ctx, true, true);
			// Adds an index to JtsGeometry class internally to compute spatial relations faster.
			shape.index();
			SpatialArgs args = new SpatialArgs(SpatialOperation.Intersects, shape);

			return strategy.makeQuery(args);
		}
		throw new PluginException("Unsupported GeoSPARQL candidate lookup policy: " + candidateLookupPolicy);
	}

	private EntityIdIterator getEntityIdsForQuery(Query query) {
		IndexReader indexReader = null;
		try {
			indexReader = openReader();
			IndexSearcher indexSearcher = new IndexSearcher(indexReader);
			return new LuceneEntityIdIterator(indexSearcher, query);
		} catch (IOException e) {
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

	IndexReader openReader() throws IOException {
		return DirectoryReader.open(directory);
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

	private void assertReadableCurrentSchema() {
		if (schemaMismatchDetected) {
			throw new PluginException(SCHEMA_MISMATCH_MESSAGE);
		}
	}

	private void assertWritableCurrentSchema() {
		if (schemaRebuildInProgress) {
			return;
		}
		assertReadableCurrentSchema();
	}

	private boolean detectSchemaMismatch() {
		try {
			if (!DirectoryReader.indexExists(directory)) {
				return false;
			}
			try (DirectoryReader reader = DirectoryReader.open(directory)) {
				if (reader.numDocs() == 0) {
					return false;
				}
				return !LuceneGeoDocumentSchema.hasCurrentSchemaCommitData(reader.getIndexCommit().getUserData());
			}
		} catch (IndexNotFoundException e) {
			return false;
		} catch (IOException e) {
			throw new PluginException("Unable to inspect GeoSPARQL Lucene index schema.", e);
		}
	}

	private void writeSchemaMarkerIfNeeded() throws IOException {
		if (!schemaMarkerPending) {
			return;
		}
		indexWriter.setLiveCommitData(LuceneGeoDocumentSchema.currentSchemaCommitData(
				indexWriter.getLiveCommitData()));
	}

	private void restoreSchemaStateAfterUncommittedRebuild() {
		if (schemaRebuildInProgress) {
			schemaMismatchDetected = schemaMismatchBeforeRebuild;
			schemaRebuildInProgress = false;
			schemaMismatchBeforeRebuild = false;
		}
	}
}
