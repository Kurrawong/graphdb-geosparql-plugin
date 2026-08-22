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

	/**
	 * Returns conservative intersection candidates with operand direction available to the adapter.
	 *
	 * <p>When the candidate is the relation subject, its source CRS is the exact-evaluation cleanup CRS. Adapters must
	 * retain candidates whose CRS84 envelope cannot safely model that cleanup.
	 */
	default CloseableIterator<CandidateEntity> getEnvelopeIntersections(
			IndexGeometry boundSourceIndexGeometry, boolean candidateIsSubject) {
		return getEnvelopeIntersections(boundSourceIndexGeometry);
	}

	/**
	 * Returns source documents retained because the candidate subject's transformed CRS84 envelope cannot model
	 * cleanup performed in that candidate's source CRS.
	 */
	default CloseableIterator<CandidateEntity> getTransformCleanupCandidates() {
		return getAllEntities();
	}

	/**
	 * Returns uncertain candidates for one bound source in a partitioned disjoint traversal.
	 *
	 * <p>Under the candidate-envelope containment contract, adapters may remove candidates whose precise envelope
	 * ordinate metadata proves they cannot be disjoint. The returned candidates carry complete source payloads.
	 * Callers own and must close the iterator. Implementations without a definite-non-match proof retain ordinary
	 * conservative envelope intersections.
	 *
	 * @param boundSourceIndexGeometry derived index envelope for one bound source geometry literal
	 * @return a closeable iterator over uncertain entity groups and their matching source geometry literal snapshots
	 */
	default CloseableIterator<CandidateEntity> getEnvelopeDisjointUncertainCandidates(
			IndexGeometry boundSourceIndexGeometry) {
		return getEnvelopeIntersections(boundSourceIndexGeometry);
	}

	default CloseableIterator<CandidateEntity> getEnvelopeDisjointUncertainCandidates(
			IndexGeometry boundSourceIndexGeometry, boolean candidateIsSubject) {
		return getEnvelopeDisjointUncertainCandidates(boundSourceIndexGeometry);
	}

	/**
	 * Returns one lightweight result per source document whose non-empty CRS84 index envelope is absent from the
	 * conservative envelope-intersection result for the supplied bound source.
	 *
	 * @param boundSourceIndexGeometry derived index envelope for one non-empty bound source geometry literal
	 * @return source-document metadata for envelope-proven disjoint candidates
	 */
	CloseableIterator<EnvelopeDisjointCandidate> getEnvelopeDisjointCandidates(
			IndexGeometry boundSourceIndexGeometry);

	default CloseableIterator<EnvelopeDisjointCandidate> getEnvelopeDisjointCandidates(
			IndexGeometry boundSourceIndexGeometry, boolean candidateIsSubject) {
		return getEnvelopeDisjointCandidates(boundSourceIndexGeometry);
	}

	/** Returns entities represented by non-spatial empty-sentinel source documents for exact evaluation. */
	CloseableIterator<CandidateEntity> getNonSpatialCandidates();

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

	/** Returns whether this indexer is retaining transaction outcome state. */
	default boolean isTransactionActive() {
		return false;
	}

	void commit() throws Exception;

	/**
	 * Makes the most recently committed index transaction final after GraphDB completes the RDF transaction.
	 */
	default void complete() throws Exception {
	}

	void rollback() throws Exception;

	/**
	 * Restores the pre-transaction index while retaining fail-closed recovery state when another durable plugin state
	 * could not be restored.
	 */
	default void rollback(boolean recoveryRequired) throws Exception {
		if (recoveryRequired) {
			throw new UnsupportedOperationException("Retaining index recovery state is not supported.");
		}
		rollback();
	}

	/** Appends the index geometry derived from one source geometry literal during a streaming full index build. */
	void appendGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry);

	void freshIndex() throws Exception;
}
