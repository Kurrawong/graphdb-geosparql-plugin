package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.GeoSparqlUnits;
import com.ontotext.trree.geosparql.jena.JenaFunctionEvaluator;
import com.useekm.indexing.GeoConstants;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by avataar on 09.04.2015..
 */
public class EntityGeometryIteratorTest {
	@Test
	public void distanceInMetresUsesJenaEvaluator() throws ValueExprEvaluationException {
		ValueFactory valueFactory = SimpleValueFactory.getInstance();
		Literal point = valueFactory.createLiteral("POINT(0 0)", GeoConstants.GEO_WKT_LITERAL);
		Literal line = valueFactory.createLiteral("LINESTRING(10 0, 10 0)", GeoConstants.GEO_WKT_LITERAL);

		Value result = JenaFunctionEvaluator.evaluate(valueFactory, GeoConstants.GEOF_DISTANCE.stringValue(),
				point, line, GeoSparqlUnits.URI_METRE);

		assertTrue(result instanceof Literal);
		assertEquals(1111950.7973436872, ((Literal) result).doubleValue(), 1e-6);
		assertEquals(valueFactory.createIRI("http://www.w3.org/2001/XMLSchema#double"), ((Literal) result).getDatatype());
	}
}
