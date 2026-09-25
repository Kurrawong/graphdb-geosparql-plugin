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

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PointCoordinateFunctionsTest {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE =
			new ValueFactoryTripleSource(VALUE_FACTORY);
	private static final List<String> FUNCTION_URIS = List.of(
			GeoConstants.GEOF_X.stringValue(), GeoConstants.GEOF_Y.stringValue(),
			GeoConstants.GEOF_Z.stringValue(), GeoConstants.GEOF_M.stringValue());

	@Test
	public void registeredFunctionsReturnSourcePointOrdinatesAsDoubles() throws Exception {
		Literal point = wkt("<http://www.opengis.net/def/crs/EPSG/0/4326> "
				+ "POINT ZM(10 100 3 7)");

		assertDouble(10.0, evaluate(GeoConstants.GEOF_X.stringValue(), point));
		assertDouble(100.0, evaluate(GeoConstants.GEOF_Y.stringValue(), point));
		assertDouble(3.0, evaluate(GeoConstants.GEOF_Z.stringValue(), point));
		assertDouble(7.0, evaluate(GeoConstants.GEOF_M.stringValue(), point));
		assertDouble(7.0, evaluate(GeoConstants.GEOF_M.stringValue(), wkt("POINT M(1 2 7)")));
	}

	@Test
	public void registeredFunctionsRejectInvalidPointArguments() {
		for (String uri : FUNCTION_URIS) {
			assertThrows(uri, ValueExprEvaluationException.class,
					() -> evaluate(uri, wkt("LINESTRING(1 2,3 4)")));
			assertThrows(uri, ValueExprEvaluationException.class,
					() -> evaluate(uri, wkt("POINT EMPTY")));
			assertThrows(uri, ValueExprEvaluationException.class, () -> evaluate(uri));
			assertThrows(uri, ValueExprEvaluationException.class,
					() -> evaluate(uri, wkt("POINT(1 2)"), wkt("POINT(3 4)")));
		}
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GeoConstants.GEOF_Z.stringValue(), wkt("POINT M(1 2 7)")));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GeoConstants.GEOF_M.stringValue(), wkt("POINT Z(1 2 7)")));
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
