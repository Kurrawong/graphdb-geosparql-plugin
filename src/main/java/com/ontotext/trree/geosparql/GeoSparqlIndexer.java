package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import org.locationtech.jts.geom.Geometry;

import java.util.List;
import java.util.function.Function;

/**
 * GeoSPARQL indexer interface.
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
	 * Returns an iterator over entities/geometries that matches the provided geometry
	 * using the provided candidate lookup policy.
	 *
	 * @param geometry an index geometry
	 * @param candidateLookupPolicy the candidate lookup policy
	 * @return an iterator over entities/geometries
	 */
	EntityGeometryIterator getMatchingObjects(Geometry geometry, CandidateLookupPolicy candidateLookupPolicy);

	/**
	 * Returns unique entity ids that match the provided index geometry with the provided candidate lookup policy.
	 *
	 * @param geometry an index geometry
	 * @param candidateLookupPolicy the candidate lookup policy
	 * @return an iterator over unique matching entity ids
	 */
	EntityIdIterator getMatchingEntityIds(Geometry geometry, CandidateLookupPolicy candidateLookupPolicy);

	/** Returns every indexed entity id, including entities represented only by non-spatial sentinel documents. */
	EntityIdIterator getAllEntityIds();

	/**
	 * Returns an iterator over all geometries for the provided entity
	 *
	 * @param subject subject id of the entity
	 * @return an iterator over entities/geometries
	 */
	EntityGeometryIterator getGeometriesFor(long subject);

	void initSettings();

	void begin() throws Exception;

	void commit() throws Exception;

	void rollback() throws Exception;

	void indexGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry);

	void freshIndex() throws Exception;
}
