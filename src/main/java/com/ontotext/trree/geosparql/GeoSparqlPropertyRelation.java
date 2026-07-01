package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.jena.JenaGeoSparqlException;
import com.ontotext.trree.geosparql.jena.JenaFunctionEvaluator;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.eclipse.rdf4j.model.IRI;

import static com.ontotext.trree.geosparql.vocabulary.GeoConstants.*;

/**
 * GeoSPARQL property relations exposed by GraphDB.
 *
 * Lucene operations are conservative candidate lookups only. Jena performs exact predicate evaluation.
 */
public enum GeoSparqlPropertyRelation {
	// Simple Features
	SF_EQUALS(GEO_SF_EQUALS, GEOF_SF_EQUALS, SpatialOperation.Intersects),
	SF_DISJOINT(GEO_SF_DISJOINT, GEOF_SF_DISJOINT, null),
	SF_INTERSECTS(GEO_SF_INTERSECTS, GEOF_SF_INTERSECTS, SpatialOperation.Intersects),
	SF_TOUCHES(GEO_SF_TOUCHES, GEOF_SF_TOUCHES, SpatialOperation.Intersects),
	SF_WITHIN(GEO_SF_WITHIN, GEOF_SF_WITHIN, SpatialOperation.Intersects),
	SF_CONTAINS(GEO_SF_CONTAINS, GEOF_SF_CONTAINS, SpatialOperation.Intersects),
	SF_OVERLAPS(GEO_SF_OVERLAPS, GEOF_SF_OVERLAPS, SpatialOperation.Intersects),
	SF_CROSSES(GEO_SF_CROSSES, GEOF_SF_CROSSES, SpatialOperation.Intersects),

	// Egenhofer
	EH_EQUALS(GEO_EH_EQUALS, GEOF_EH_EQUALS, SpatialOperation.Intersects),
	EH_DISJOINT(GEO_EH_DISJOINT, GEOF_EH_DISJOINT, null),
	EH_MEET(GEO_EH_MEET, GEOF_EH_MEET, SpatialOperation.Intersects),
	EH_OVERLAP(GEO_EH_OVERLAP, GEOF_EH_OVERLAP, SpatialOperation.Intersects),
	EH_COVERS(GEO_EH_COVERS, GEOF_EH_COVERS, SpatialOperation.Intersects),
	EH_COVERED_BY(GEO_EH_COVERED_BY, GEOF_EH_COVERED_BY, SpatialOperation.Intersects),
	EH_INSIDE(GEO_EH_INSIDE, GEOF_EH_INSIDE, SpatialOperation.Intersects),
	EH_CONTAINS(GEO_EH_CONTAINS, GEOF_EH_CONTAINS, SpatialOperation.Intersects),

	// RCC8
	RCC8_EQ(GEO_RCC8_EQ, GEOF_RCC8_EQ, SpatialOperation.Intersects),
	RCC8_DC(GEO_RCC8_DC, GEOF_RCC8_DC, null),
	RCC8_EC(GEO_RCC8_EC, GEOF_RCC8_EC, SpatialOperation.Intersects),
	RCC8_PO(GEO_RCC8_PO, GEOF_RCC8_PO, SpatialOperation.Intersects),
	RCC8_TPPI(GEO_RCC8_TPPI, GEOF_RCC8_TPPI, SpatialOperation.Intersects),
	RCC8_TPP(GEO_RCC8_TPP, GEOF_RCC8_TPP, SpatialOperation.Intersects),
	RCC8_NTPP(GEO_RCC8_NTPP, GEOF_RCC8_NTPP, SpatialOperation.Intersects),
	RCC8_NTPPI(GEO_RCC8_NTPPI, GEOF_RCC8_NTPPI, SpatialOperation.Intersects);

	private final IRI predicateUri;
	private final IRI filterFunctionUri;
	private final SpatialOperation spatialOperation;

	GeoSparqlPropertyRelation(IRI predicateUri, IRI filterFunctionUri, SpatialOperation spatialOperation) {
		this.predicateUri = predicateUri;
		this.filterFunctionUri = filterFunctionUri;
		this.spatialOperation = spatialOperation;
	}

	public IRI getPredicateUri() {
		return predicateUri;
	}

	public SpatialOperation getSpatialOperation() {
		return spatialOperation;
	}

	public SpatialOperation getInverseSpatialOperation() {
		return spatialOperation;
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
