package com.ontotext.trree.geosparql;

/**
 * Lucene candidate lookup policy used before exact predicate evaluation.
 */
public enum CandidateLookupPolicy {
	INTERSECTS,
	FULL_SCAN
}
