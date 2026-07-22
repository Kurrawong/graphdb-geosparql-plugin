package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;

import java.util.List;
import java.util.function.Function;

/**
 * Plugin-internal seam between GraphDB repository handling and GeoSPARQL candidate indexing.
 *
 * <p>Writes store derived index geometries. Reads return those values for coarse candidate lookup while preserving
 * their source geometry literal snapshots for the later exact-evaluation step. Public visibility supports the Lucene
 * adapter in a sibling package.
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
	 * Returns candidates for one complete bound source geometry literal. The adapter keeps the relation-specific policy
	 * for ordinary sources and applies conservative envelope handling for generic collections.
	 *
	 * @param boundSourceIndexGeometry derived index geometry for one bound source geometry literal
	 * @param candidateLookupPolicy spatial candidate policy; {@link CandidateLookupPolicy#FULL_SCAN} must use
	 *                              {@link #getAllEntities()}
	 * @return a closeable iterator over entity groups and their matching source geometry literal snapshots
	 */
	CloseableIterator<CandidateEntity> getCandidatesForSource(IndexGeometry boundSourceIndexGeometry,
			CandidateLookupPolicy candidateLookupPolicy);

	/** Returns every indexed entity, including entities represented only by non-spatial empty sentinels. */
	CloseableIterator<CandidateEntity> getAllEntities();

	/**
	 * Streams all stored index geometries for one entity. A non-positive id selects every stored document for internal
	 * schema and lifecycle reads.
	 *
	 * @param subject subject id of the entity
	 * @return a closeable iterator over index geometries
	 */
	CloseableIterator<IndexGeometry> getGeometriesFor(long subject);

	void initSettings();

	void begin() throws Exception;

	void commit() throws Exception;

	void rollback() throws Exception;

	/** Appends the index geometry derived from one source geometry literal during a streaming full index build. */
	void appendGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry);

	void freshIndex() throws Exception;
}
