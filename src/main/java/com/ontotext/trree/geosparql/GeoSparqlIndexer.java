package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;

import java.util.List;
import java.util.function.Function;

/**
 * Plugin-internal seam between GraphDB repository handling and GeoSPARQL candidate indexing.
 *
 * <p>Writes store derived index geometries. Candidate reads return entity groups with matching source geometry literal
 * snapshots, while bound-entity reads return stored source geometry literal snapshots directly. Public visibility
 * supports the Lucene adapter in a sibling package.
 */
public interface GeoSparqlIndexer {
	/**
	 * Initializes the indexer.
	 */
	void initialize() throws Exception;

	/**
	 * Replaces all indexed geometry documents for an entity during an incremental update.
	 *
	 * @param subject  id of the subject
	 * @param geometries geometries that corresponds to asWKT/asGML's object
	 */
	void indexGeometryList(long subject, Function<Long, String> subjectMapper, List<IndexGeometry> geometries);

	/**
	 * Returns candidates whose CRS84 index envelopes intersect one bound source envelope.
	 *
	 * @param boundSourceIndexGeometry derived index envelope for one bound source geometry literal
	 * @return a closeable iterator over entity groups and their matching source geometry literal snapshots
	 */
	CloseableIterator<CandidateEntity> getEnvelopeIntersections(IndexGeometry boundSourceIndexGeometry);

	/** Returns every indexed entity, including entities represented only by non-spatial empty sentinels. */
	CloseableIterator<CandidateEntity> getAllEntities();

	/**
	 * Streams all stored source geometry literal snapshots for one entity. A non-positive id selects every stored
	 * document for internal schema and lifecycle reads.
	 *
	 * @param subject subject id of the entity
	 * @return a closeable iterator over source geometry literal snapshots
	 */
	CloseableIterator<SourceGeometryLiteral> getSourceGeometryLiteralsFor(long subject);

	void initSettings();

	void begin() throws Exception;

	void commit() throws Exception;

	void rollback() throws Exception;

	/** Appends the index geometry derived from one source geometry literal during a streaming full index build. */
	void appendGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry);

	void freshIndex() throws Exception;
}
