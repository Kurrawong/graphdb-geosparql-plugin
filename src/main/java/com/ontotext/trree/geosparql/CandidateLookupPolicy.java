package com.ontotext.trree.geosparql;

/**
 * Lucene candidate lookup policy used before exact predicate evaluation.
 */
public enum CandidateLookupPolicy {
	FULL_SCAN,
	INTERSECTS,
	DISJOINT,
	EQUALS,
	WITHIN,
	CONTAINS,
	OVERLAPS
}
