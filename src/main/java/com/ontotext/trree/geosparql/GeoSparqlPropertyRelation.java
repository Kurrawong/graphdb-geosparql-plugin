package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.jena.JenaGeoSparqlException;
import com.ontotext.trree.geosparql.jena.JenaFunctionEvaluator;
import org.eclipse.rdf4j.model.IRI;

import static com.ontotext.trree.geosparql.vocabulary.GeoConstants.*;

/**
 * GeoSPARQL property relations exposed by GraphDB.
 *
 * <p>The normal candidate policy applies when the subject is unknown and the object is bound; the inverse policy
 * applies when the object is unknown and the subject is bound. A candidate policy may be the same spatial relation
 * or a safe superset such as {@code INTERSECTS} for touches, crosses, meet, and externally connected. Jena always
 * performs exact predicate evaluation in subject/object argument order.
 */
public enum GeoSparqlPropertyRelation {
	// Simple Features
	SF_EQUALS(GEO_SF_EQUALS, GEOF_SF_EQUALS, CandidateLookupPolicy.EQUALS),
	SF_DISJOINT(GEO_SF_DISJOINT, GEOF_SF_DISJOINT, CandidateLookupPolicy.DISJOINT),
	SF_INTERSECTS(GEO_SF_INTERSECTS, GEOF_SF_INTERSECTS, CandidateLookupPolicy.INTERSECTS),
	SF_TOUCHES(GEO_SF_TOUCHES, GEOF_SF_TOUCHES, CandidateLookupPolicy.INTERSECTS),
	SF_WITHIN(GEO_SF_WITHIN, GEOF_SF_WITHIN,
			CandidateLookupPolicy.WITHIN, CandidateLookupPolicy.CONTAINS),
	SF_CONTAINS(GEO_SF_CONTAINS, GEOF_SF_CONTAINS,
			CandidateLookupPolicy.CONTAINS, CandidateLookupPolicy.WITHIN),
	SF_OVERLAPS(GEO_SF_OVERLAPS, GEOF_SF_OVERLAPS, CandidateLookupPolicy.OVERLAPS),
	SF_CROSSES(GEO_SF_CROSSES, GEOF_SF_CROSSES, CandidateLookupPolicy.INTERSECTS),

	// Egenhofer
	EH_EQUALS(GEO_EH_EQUALS, GEOF_EH_EQUALS, CandidateLookupPolicy.EQUALS),
	EH_DISJOINT(GEO_EH_DISJOINT, GEOF_EH_DISJOINT, CandidateLookupPolicy.DISJOINT),
	EH_MEET(GEO_EH_MEET, GEOF_EH_MEET, CandidateLookupPolicy.INTERSECTS),
	EH_OVERLAP(GEO_EH_OVERLAP, GEOF_EH_OVERLAP, CandidateLookupPolicy.OVERLAPS),
	EH_COVERS(GEO_EH_COVERS, GEOF_EH_COVERS,
			CandidateLookupPolicy.CONTAINS, CandidateLookupPolicy.WITHIN),
	EH_COVERED_BY(GEO_EH_COVERED_BY, GEOF_EH_COVERED_BY,
			CandidateLookupPolicy.WITHIN, CandidateLookupPolicy.CONTAINS),
	EH_INSIDE(GEO_EH_INSIDE, GEOF_EH_INSIDE,
			CandidateLookupPolicy.WITHIN, CandidateLookupPolicy.CONTAINS),
	EH_CONTAINS(GEO_EH_CONTAINS, GEOF_EH_CONTAINS,
			CandidateLookupPolicy.CONTAINS, CandidateLookupPolicy.WITHIN),

	// RCC8
	RCC8_EQ(GEO_RCC8_EQ, GEOF_RCC8_EQ, CandidateLookupPolicy.EQUALS),
	RCC8_DC(GEO_RCC8_DC, GEOF_RCC8_DC, CandidateLookupPolicy.DISJOINT),
	RCC8_EC(GEO_RCC8_EC, GEOF_RCC8_EC, CandidateLookupPolicy.INTERSECTS),
	RCC8_PO(GEO_RCC8_PO, GEOF_RCC8_PO, CandidateLookupPolicy.OVERLAPS),
	RCC8_TPPI(GEO_RCC8_TPPI, GEOF_RCC8_TPPI,
			CandidateLookupPolicy.CONTAINS, CandidateLookupPolicy.WITHIN),
	RCC8_TPP(GEO_RCC8_TPP, GEOF_RCC8_TPP,
			CandidateLookupPolicy.WITHIN, CandidateLookupPolicy.CONTAINS),
	RCC8_NTPP(GEO_RCC8_NTPP, GEOF_RCC8_NTPP,
			CandidateLookupPolicy.WITHIN, CandidateLookupPolicy.CONTAINS),
	RCC8_NTPPI(GEO_RCC8_NTPPI, GEOF_RCC8_NTPPI,
			CandidateLookupPolicy.CONTAINS, CandidateLookupPolicy.WITHIN);

	private final IRI predicateUri;
	private final IRI filterFunctionUri;
	private final CandidateLookupPolicy candidateLookupPolicy;
	private final CandidateLookupPolicy inverseCandidateLookupPolicy;

	GeoSparqlPropertyRelation(IRI predicateUri, IRI filterFunctionUri,
							   CandidateLookupPolicy symmetricCandidateLookupPolicy) {
		this(predicateUri, filterFunctionUri, symmetricCandidateLookupPolicy, symmetricCandidateLookupPolicy);
	}

	GeoSparqlPropertyRelation(IRI predicateUri, IRI filterFunctionUri,
							   CandidateLookupPolicy candidateLookupPolicy,
							   CandidateLookupPolicy inverseCandidateLookupPolicy) {
		this.predicateUri = predicateUri;
		this.filterFunctionUri = filterFunctionUri;
		this.candidateLookupPolicy = candidateLookupPolicy;
		this.inverseCandidateLookupPolicy = inverseCandidateLookupPolicy;
	}

	public IRI getPredicateUri() {
		return predicateUri;
	}

	public CandidateLookupPolicy getCandidateLookupPolicy() {
		return candidateLookupPolicy;
	}

	public CandidateLookupPolicy getInverseCandidateLookupPolicy() {
		return inverseCandidateLookupPolicy;
	}

	public boolean evaluate(SourceGeometryLiteral argument1, SourceGeometryLiteral argument2) {
		try {
			return JenaFunctionEvaluator.evaluateTopological(filterFunctionUri.stringValue(), argument1, argument2);
		} catch (JenaGeoSparqlException e) {
			throw e;
		} catch (Exception e) {
			throw new JenaGeoSparqlException("Unable to evaluate GeoSPARQL relation " + predicateUri, e);
		}
	}
}
