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
	public void disjointRelationsFullScanAndAllOthersUseEnvelopeIntersection() {
		for (GeoSparqlPropertyRelation relation : GeoSparqlPropertyRelation.values()) {
			CandidateLookupPolicy expected = FULL_SCAN_RELATIONS.contains(relation)
					? CandidateLookupPolicy.FULL_SCAN
					: CandidateLookupPolicy.ENVELOPE_INTERSECTS;
			assertEquals(relation + " candidate policy", expected, relation.getCandidateLookupPolicy());
		}
	}
}
