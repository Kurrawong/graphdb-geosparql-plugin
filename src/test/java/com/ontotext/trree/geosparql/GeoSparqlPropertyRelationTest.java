package com.ontotext.trree.geosparql;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class GeoSparqlPropertyRelationTest {
	private static final Map<GeoSparqlPropertyRelation, ExpectedPolicies> EXPECTED_POLICIES = Map.ofEntries(
			policies(GeoSparqlPropertyRelation.SF_EQUALS, CandidateLookupPolicy.EQUALS),
			policies(GeoSparqlPropertyRelation.SF_DISJOINT, CandidateLookupPolicy.DISJOINT),
			policies(GeoSparqlPropertyRelation.SF_INTERSECTS, CandidateLookupPolicy.INTERSECTS),
			policies(GeoSparqlPropertyRelation.SF_TOUCHES, CandidateLookupPolicy.INTERSECTS),
			policies(GeoSparqlPropertyRelation.SF_WITHIN,
					CandidateLookupPolicy.WITHIN, CandidateLookupPolicy.CONTAINS),
			policies(GeoSparqlPropertyRelation.SF_CONTAINS,
					CandidateLookupPolicy.CONTAINS, CandidateLookupPolicy.WITHIN),
			policies(GeoSparqlPropertyRelation.SF_OVERLAPS, CandidateLookupPolicy.OVERLAPS),
			policies(GeoSparqlPropertyRelation.SF_CROSSES, CandidateLookupPolicy.INTERSECTS),
			policies(GeoSparqlPropertyRelation.EH_EQUALS, CandidateLookupPolicy.EQUALS),
			policies(GeoSparqlPropertyRelation.EH_DISJOINT, CandidateLookupPolicy.DISJOINT),
			policies(GeoSparqlPropertyRelation.EH_MEET, CandidateLookupPolicy.INTERSECTS),
			policies(GeoSparqlPropertyRelation.EH_OVERLAP, CandidateLookupPolicy.OVERLAPS),
			policies(GeoSparqlPropertyRelation.EH_COVERS,
					CandidateLookupPolicy.CONTAINS, CandidateLookupPolicy.WITHIN),
			policies(GeoSparqlPropertyRelation.EH_COVERED_BY,
					CandidateLookupPolicy.WITHIN, CandidateLookupPolicy.CONTAINS),
			policies(GeoSparqlPropertyRelation.EH_INSIDE,
					CandidateLookupPolicy.WITHIN, CandidateLookupPolicy.CONTAINS),
			policies(GeoSparqlPropertyRelation.EH_CONTAINS,
					CandidateLookupPolicy.CONTAINS, CandidateLookupPolicy.WITHIN),
			policies(GeoSparqlPropertyRelation.RCC8_EQ, CandidateLookupPolicy.EQUALS),
			policies(GeoSparqlPropertyRelation.RCC8_DC, CandidateLookupPolicy.DISJOINT),
			policies(GeoSparqlPropertyRelation.RCC8_EC, CandidateLookupPolicy.INTERSECTS),
			policies(GeoSparqlPropertyRelation.RCC8_PO, CandidateLookupPolicy.OVERLAPS),
			policies(GeoSparqlPropertyRelation.RCC8_TPPI,
					CandidateLookupPolicy.CONTAINS, CandidateLookupPolicy.WITHIN),
			policies(GeoSparqlPropertyRelation.RCC8_TPP,
					CandidateLookupPolicy.WITHIN, CandidateLookupPolicy.CONTAINS),
			policies(GeoSparqlPropertyRelation.RCC8_NTPP,
					CandidateLookupPolicy.WITHIN, CandidateLookupPolicy.CONTAINS),
			policies(GeoSparqlPropertyRelation.RCC8_NTPPI,
					CandidateLookupPolicy.CONTAINS, CandidateLookupPolicy.WITHIN));

	@Test
	public void relationsDeclareExpectedForwardAndInverseCandidateLookups() {
		assertEquals(GeoSparqlPropertyRelation.values().length, EXPECTED_POLICIES.size());
		for (GeoSparqlPropertyRelation relation : GeoSparqlPropertyRelation.values()) {
			ExpectedPolicies expected = EXPECTED_POLICIES.get(relation);
			assertEquals(relation + " forward policy", expected.forward, relation.getCandidateLookupPolicy());
			assertEquals(relation + " inverse policy", expected.inverse,
					relation.getInverseCandidateLookupPolicy());
		}
	}

	private static Map.Entry<GeoSparqlPropertyRelation, ExpectedPolicies> policies(
			GeoSparqlPropertyRelation relation, CandidateLookupPolicy symmetricPolicy) {
		return policies(relation, symmetricPolicy, symmetricPolicy);
	}

	private static Map.Entry<GeoSparqlPropertyRelation, ExpectedPolicies> policies(
			GeoSparqlPropertyRelation relation, CandidateLookupPolicy forward, CandidateLookupPolicy inverse) {
		return Map.entry(relation, new ExpectedPolicies(forward, inverse));
	}

	private record ExpectedPolicies(CandidateLookupPolicy forward, CandidateLookupPolicy inverse) {
	}
}
