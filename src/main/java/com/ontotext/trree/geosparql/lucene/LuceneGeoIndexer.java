package com.ontotext.trree.geosparql.lucene;

import com.ontotext.trree.geosparql.CandidateEntity;
import com.ontotext.trree.geosparql.CloseableIterator;
import com.ontotext.trree.geosparql.GeoSparqlConfig;
import com.ontotext.trree.geosparql.GeoSparqlIndexer;
import com.ontotext.trree.geosparql.GeoSparqlPlugin;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
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
import org.locationtech.jts.geom.Envelope;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Lucene-backed GeoSPARQL candidate indexer.
 *
 * <p>The Lucene index stores one CRS84 source envelope for coarse candidate lookup, plus native-CRS source geometry
 * WKB and literal metadata for later CRS-aware exact evaluation. Each source geometry literal has one Lucene
 * document. Candidate queries group matching source documents by entity, while direct entity reads stream source
 * geometry literal snapshots. Returned iterators own their Lucene readers and are closed by their callers.
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

	private SpatialContext ctx;

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

		this.ctx = SpatialContext.GEO;

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

		this.strategy = new RecursivePrefixTreeStrategy(grid, LuceneGeoDocumentSchema.FIELD_SPATIAL_PREFIX);
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
			List<org.apache.lucene.document.Document> documents = LuceneGeoDocumentSchema.toDocuments(
					subject, geometries, strategy, ctx);
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
	public void appendGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry) {
		assertWritableCurrentSchema();
		try {
			indexWriter.addDocument(LuceneGeoDocumentSchema.toDocument(subject, geometry, strategy, ctx));
			schemaMarkerPending = true;
		} catch (Exception e) {
			handleCreateDocumentUnhandledException(subject, subjectMapper, e);
		}
	}

	@Override
	public CloseableIterator<CandidateEntity> getEnvelopeIntersections(IndexGeometry boundSourceIndexGeometry) {
		assertReadableCurrentSchema();
		if (boundSourceIndexGeometry == null || !boundSourceIndexGeometry.isSpatialCandidate()) {
			return getCandidateEntitiesForQuery(new MatchNoDocsQuery("Empty source has no envelope intersections."));
		}
		return getCandidateEntitiesForQuery(strategy.makeQuery(
				new SpatialArgs(SpatialOperation.Intersects, envelopeShape(boundSourceIndexGeometry))));
	}

	@Override
	public CloseableIterator<CandidateEntity> getAllEntities() {
		assertReadableCurrentSchema();
		return getCandidateEntitiesForQuery(LuceneGeoDocumentSchema.allDocumentsQuery());
	}

	@Override
	public CloseableIterator<SourceGeometryLiteral> getSourceGeometryLiteralsFor(long subject) {
		assertReadableCurrentSchema();
		final Query query;
		if (subject > 0) {
			query = LuceneGeoDocumentSchema.entityIdQuery(subject);
		} else {
			query = LuceneGeoDocumentSchema.allDocumentsQuery();
		}

		return getSourceGeometryLiteralsForQuery(query);
	}

	private CloseableIterator<SourceGeometryLiteral> getSourceGeometryLiteralsForQuery(Query query) {
		IndexReader indexReader = null;
		try {
			indexReader = openReader();
			IndexSearcher indexSearcher = new IndexSearcher(indexReader);
			return new LuceneSourceGeometryLiteralIterator(indexSearcher, query);
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

	private Rectangle envelopeShape(IndexGeometry indexGeometry) {
		Envelope envelope = indexGeometry.indexEnvelope();
		return ctx.getShapeFactory().rect(
				envelope.getMinX(), envelope.getMaxX(), envelope.getMinY(), envelope.getMaxY());
	}

	private CloseableIterator<CandidateEntity> getCandidateEntitiesForQuery(Query query) {
		try {
			IndexReader indexReader = openReader();
			IndexSearcher indexSearcher = new IndexSearcher(indexReader);
			return new LuceneCandidateEntityIterator(indexSearcher, query);
		} catch (IOException e) {
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
