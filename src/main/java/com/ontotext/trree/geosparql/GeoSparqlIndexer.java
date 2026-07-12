package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import org.locationtech.jts.geom.Geometry;

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
	 * Indexes an entity (Geometry) with the predicate asWKT/asGML.
	 *
	 * @param subject  id of the subject
	 * @param geometries geometries that corresponds to asWKT/asGML's object
	 */
	void indexGeometryList(long subject, Function<Long, String> subjectMapper, List<IndexGeometry> geometries);

	/**
	 * Returns immutable entity groups whose Lucene documents match the candidate lookup.
	 *
	 * @param geometry an index geometry
	 * @param candidateLookupPolicy the candidate lookup policy
	 * @return a closeable iterator over grouped matching entities
	 */
	CloseableIterator<CandidateEntity> getMatchingEntities(Geometry geometry,
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

	void indexGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry);

	void freshIndex() throws Exception;
}
