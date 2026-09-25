package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.function.Function;
import org.eclipse.rdf4j.query.algebra.evaluation.function.FunctionRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class DirectionalCoordinateFunctionsTest {
	private static final String EASTING = GeoConstants.NS_GEOF + "easting";
	private static final String NORTHING = GeoConstants.NS_GEOF + "northing";
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE =
			new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void directionalCoordinatesFollowCrsAxisDirections() throws Exception {
		Literal epsg4326 = wkt("<http://www.opengis.net/def/crs/EPSG/0/4326> POINT(10 100)");
		Literal crs84 = wkt("POINT(100 10)");
		Literal projected = wkt("<http://www.opengis.net/def/crs/EPSG/0/32634> "
				+ "POINT(500000 4600000)");

		assertDouble(100.0, evaluate(EASTING, epsg4326));
		assertDouble(10.0, evaluate(NORTHING, epsg4326));
		assertDouble(100.0, evaluate(EASTING, crs84));
		assertDouble(10.0, evaluate(NORTHING, crs84));
		assertDouble(500000.0, evaluate(EASTING, projected));
		assertDouble(4600000.0, evaluate(NORTHING, projected));
	}

	@Test
	public void directionalCoordinatesRejectNonPointsAndEmptyPoints() {
		for (String uri : new String[]{EASTING, NORTHING}) {
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(uri, wkt("LINESTRING(1 2,3 4)")));
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(uri, wkt("POINT EMPTY")));
		}
	}

	private Value evaluate(String uri, Value... args) throws ValueExprEvaluationException {
		GeoSparqlFunctionRegistration.registerAll();
		Function function = FunctionRegistry.getInstance().get(uri)
				.orElseThrow(() -> new AssertionError("Function not registered: " + uri));
		return function.evaluate(TRIPLE_SOURCE, args);
	}

	private Literal wkt(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL);
	}

	private void assertDouble(double expected, Value value) {
		Literal literal = (Literal) value;
		assertEquals(XSD.DOUBLE, literal.getDatatype());
		assertEquals(expected, literal.doubleValue(), 0.0);
	}
}
