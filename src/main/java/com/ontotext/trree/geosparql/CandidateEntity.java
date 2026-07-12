package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;

import java.util.List;

/**
 * Immutable result for one entity returned by a Lucene candidate lookup.
 *
 * <p>The geometry list contains values decoded from the Lucene documents matched by that lookup. It is the complete
 * entity geometry set for a full scan, but may be a lookup-specific subset for a spatial query. Each
 * {@link IndexGeometry} retains the source geometry literal snapshot used by exact evaluation.
 *
 * <p>This type is plugin-internal; public visibility allows the Lucene adapter to return it across packages.
 */
public final class CandidateEntity {
	private final long entityId;
	private final List<IndexGeometry> matchingGeometries;

	/**
	 * Creates a candidate group with a positive GraphDB entity id and at least one matching geometry.
	 * The supplied list is defensively copied.
	 *
	 * @param entityId positive GraphDB entity id shared by the matching documents
	 * @param matchingGeometries non-empty geometry values decoded from those documents
	 */
	public CandidateEntity(long entityId, List<IndexGeometry> matchingGeometries) {
		if (entityId <= 0) {
			throw new IllegalArgumentException("Candidate entity id must be positive.");
		}
		this.matchingGeometries = List.copyOf(matchingGeometries);
		if (this.matchingGeometries.isEmpty()) {
			throw new IllegalArgumentException("Candidate entity must contain at least one matching index geometry.");
		}
		this.entityId = entityId;
	}

	/** Returns the GraphDB entity id shared by the matching Lucene documents. */
	public long entityId() {
		return entityId;
	}

	/** Returns the immutable matching geometry snapshot for this lookup. */
	public List<IndexGeometry> matchingGeometries() {
		return matchingGeometries;
	}
}
