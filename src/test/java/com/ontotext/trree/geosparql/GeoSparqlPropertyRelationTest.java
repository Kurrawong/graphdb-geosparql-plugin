package com.ontotext.trree.geosparql;

import org.junit.Test;
import org.locationtech.jts.geom.Dimension;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeoSparqlPropertyRelationTest {
	private static final int[] NON_EMPTY_TOPOLOGICAL_DIMENSIONS = {
			Dimension.P, Dimension.L, Dimension.A
	};
	private static final Set<GeoSparqlPropertyRelation> PARTITIONED_DISJOINT_RELATIONS = Set.of(
			GeoSparqlPropertyRelation.SF_DISJOINT,
			GeoSparqlPropertyRelation.EH_DISJOINT,
			GeoSparqlPropertyRelation.RCC8_DC);

	@Test
	public void disjointRelationsUsePartitionedLookupAndAllOthersUseEnvelopeIntersection() {
		for (GeoSparqlPropertyRelation relation : GeoSparqlPropertyRelation.values()) {
			CandidateLookupPolicy expected = PARTITIONED_DISJOINT_RELATIONS.contains(relation)
					? CandidateLookupPolicy.DISJOINT_PARTITIONED
					: CandidateLookupPolicy.ENVELOPE_INTERSECTS;
			assertEquals(relation + " candidate policy", expected, relation.getCandidateLookupPolicy());
		}
	}

	@Test
	public void envelopeIntersectionPolicyHasNoDisjointPartitionCapability() {
		for (GeoSparqlPropertyRelation relation : GeoSparqlPropertyRelation.values()) {
			if (relation.getCandidateLookupPolicy() != CandidateLookupPolicy.ENVELOPE_INTERSECTS) {
				continue;
			}
			for (int candidateDimension : NON_EMPTY_TOPOLOGICAL_DIMENSIONS) {
				for (int boundDimension : NON_EMPTY_TOPOLOGICAL_DIMENSIONS) {
					assertFalse(relation + " definite envelope capability",
							relation.envelopeDisjointIsDefiniteMatch(candidateDimension, boundDimension));
				}
			}
			for (int boundDimension : NON_EMPTY_TOPOLOGICAL_DIMENSIONS) {
				assertFalse(relation + " bound envelope capability",
						relation.boundSourceCanParticipateInDisjointPartition(boundDimension));
			}
		}
	}

	@Test
	public void sfAndEhAcceptEveryNonEmptyDimensionWhileRcc8RequiresTwoAreas() {
		for (int candidateDimension : NON_EMPTY_TOPOLOGICAL_DIMENSIONS) {
			for (int boundDimension : NON_EMPTY_TOPOLOGICAL_DIMENSIONS) {
				assertTrue(GeoSparqlPropertyRelation.SF_DISJOINT
						.envelopeDisjointIsDefiniteMatch(candidateDimension, boundDimension));
				assertTrue(GeoSparqlPropertyRelation.EH_DISJOINT
						.envelopeDisjointIsDefiniteMatch(candidateDimension, boundDimension));
				assertEquals(candidateDimension == Dimension.A && boundDimension == Dimension.A,
						GeoSparqlPropertyRelation.RCC8_DC
								.envelopeDisjointIsDefiniteMatch(candidateDimension, boundDimension));
			}
		}
		for (int boundDimension : NON_EMPTY_TOPOLOGICAL_DIMENSIONS) {
			assertTrue(GeoSparqlPropertyRelation.SF_DISJOINT
					.boundSourceCanParticipateInDisjointPartition(boundDimension));
			assertTrue(GeoSparqlPropertyRelation.EH_DISJOINT
					.boundSourceCanParticipateInDisjointPartition(boundDimension));
			assertEquals(boundDimension == Dimension.A,
					GeoSparqlPropertyRelation.RCC8_DC
							.boundSourceCanParticipateInDisjointPartition(boundDimension));
		}
	}
}
