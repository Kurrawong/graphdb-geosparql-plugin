package com.ontotext.trree.geosparql;

/**
 * Lucene candidate lookup policy used before exact predicate evaluation.
 */
public enum CandidateLookupPolicy {
	ENVELOPE_INTERSECTS,
	FULL_SCAN
}
