package com.ontotext.trree.geosparql;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

public class GeoSparqlPropertyRelationTest {
	private static final Set<GeoSparqlPropertyRelation> FULL_SCAN_RELATIONS = Set.of(
			GeoSparqlPropertyRelation.SF_DISJOINT,
			GeoSparqlPropertyRelation.EH_DISJOINT,
			GeoSparqlPropertyRelation.RCC8_DC);

	@Test
	public void disjointRelationsUseFullScanCandidateLookup() {
		for (GeoSparqlPropertyRelation relation : FULL_SCAN_RELATIONS) {
			assertEquals(CandidateLookupPolicy.FULL_SCAN, relation.getCandidateLookupPolicy());
		}
	}

	@Test
	public void nonDisjointRelationsUseIntersectsCandidateLookup() {
		for (GeoSparqlPropertyRelation relation : GeoSparqlPropertyRelation.values()) {
			if (!FULL_SCAN_RELATIONS.contains(relation)) {
				assertEquals(CandidateLookupPolicy.INTERSECTS, relation.getCandidateLookupPolicy());
			}
		}
	}

	@Test
	public void inversePoliciesMatchForwardPolicies() {
		for (GeoSparqlPropertyRelation relation : GeoSparqlPropertyRelation.values()) {
			assertEquals(relation.getCandidateLookupPolicy(), relation.getInverseCandidateLookupPolicy());
		}
	}
}
