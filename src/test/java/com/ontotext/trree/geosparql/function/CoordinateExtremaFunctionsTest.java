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
import static org.junit.Assert.assertTrue;

public class CoordinateExtremaFunctionsTest {
	private static final String MAX_X_URI = GeoConstants.NS_GEOF + "maxX";
	private static final String MAX_Y_URI = GeoConstants.NS_GEOF + "maxY";
	private static final String MAX_Z_URI = GeoConstants.NS_GEOF + "maxZ";
	private static final String MIN_X_URI = GeoConstants.NS_GEOF + "minX";
	private static final String MIN_Y_URI = GeoConstants.NS_GEOF + "minY";
	private static final String MIN_Z_URI = GeoConstants.NS_GEOF + "minZ";
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE =
			new ValueFactoryTripleSource(VALUE_FACTORY);
	private static final List<String> FUNCTION_URIS = List.of(
			MAX_X_URI, MAX_Y_URI, MAX_Z_URI, MIN_X_URI, MIN_Y_URI, MIN_Z_URI);

	@Test
	public void minXReturnsTheLeastCrs84CoordinateAsDouble() throws Exception {
		Value result = evaluate(MIN_X_URI,
				wkt("MULTIPOINT((5 2),(-3 7),(1 -4))"));

		assertTrue(result instanceof Literal);
		assertEquals(-3.0, ((Literal) result).doubleValue(), 0.0);
		assertEquals(XSD.DOUBLE, ((Literal) result).getDatatype());
	}

	@Test
	public void xyExtremaScanAtomicMultiAndGenericCollectionMembers() throws Exception {
		Literal collection = wkt("GEOMETRYCOLLECTION("
				+ "POINT(5 2),MULTIPOINT((-3 7),(1 -4)))");

		assertDouble(5.0, evaluate(MAX_X_URI, collection));
		assertDouble(-4.0, evaluate(MIN_Y_URI, collection));
		assertDouble(7.0, evaluate(MAX_Y_URI, collection));
	}

	@Test
	public void zExtremaReturnFiniteValuesFromAZBearingLayout() throws Exception {
		Literal line = wkt("LINESTRING Z(0 0 5,1 1 -4,2 2 9)");
		Literal measuredLine = wkt("LINESTRING ZM(0 0 5 50,1 1 -4 60,2 2 9 70)");

		assertDouble(-4.0, evaluate(MIN_Z_URI, line));
		assertDouble(9.0, evaluate(MAX_Z_URI, line));
		assertDouble(-4.0, evaluate(MIN_Z_URI, measuredLine));
		assertDouble(9.0, evaluate(MAX_Z_URI, measuredLine));
	}

	@Test
	public void xyExtremaFollowSourceCrsAxisOrder() throws Exception {
		Literal point = wkt("<http://www.opengis.net/def/crs/EPSG/0/4326> POINT(10 100)");

		assertDouble(10.0, evaluate(MIN_X_URI, point));
		assertDouble(10.0, evaluate(MAX_X_URI, point));
		assertDouble(100.0, evaluate(MIN_Y_URI, point));
		assertDouble(100.0, evaluate(MAX_Y_URI, point));
	}

	@Test
	public void zExtremaPreserveZInAuthorityAxisOrder() throws Exception {
		String crs = "<http://www.opengis.net/def/crs/EPSG/0/4326> ";
		Literal point = wkt(crs + "POINT Z(10 100 7)");
		Literal measuredPoint = wkt(crs + "POINT ZM(10 100 7 42)");

		assertDouble(7.0, evaluate(MIN_Z_URI, point));
		assertDouble(7.0, evaluate(MAX_Z_URI, point));
		assertDouble(7.0, evaluate(MIN_Z_URI, measuredPoint));
		assertDouble(7.0, evaluate(MAX_Z_URI, measuredPoint));
	}

	@Test
	public void extremaRejectEmptyAndIneligibleCoordinateLayouts() {
		for (String empty : List.of(
				"POINT EMPTY", "MULTIPOINT EMPTY", "GEOMETRYCOLLECTION EMPTY")) {
			for (String functionUri : FUNCTION_URIS) {
				assertThrows(functionUri, ValueExprEvaluationException.class,
						() -> evaluate(functionUri, wkt(empty)));
			}
		}

		for (String functionUri : List.of(MIN_Z_URI, MAX_Z_URI)) {
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, wkt("POINT(1 2)")));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, wkt("POINT M(1 2 99)")));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, wkt("POINT Z EMPTY")));
		}
	}

	@Test
	public void mixedCollectionUsesJenaLayoutAndCoordinateSequences() throws Exception {
		Literal mixed = wkt("GEOMETRYCOLLECTION(POINT Z(1 2 3),POINT M(4 5 6))");

		assertDouble(1.0, evaluate(MIN_X_URI, mixed));
		assertDouble(4.0, evaluate(MAX_X_URI, mixed));
		assertDouble(2.0, evaluate(MIN_Y_URI, mixed));
		assertDouble(5.0, evaluate(MAX_Y_URI, mixed));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(MIN_Z_URI, mixed));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(MAX_Z_URI, mixed));
	}

	@Test
	public void extremaEnforceGeometryArgumentsAndMandatoryArity() {
		Literal point = wkt("POINT(1 2)");
		Value geometryIri = VALUE_FACTORY.createIRI("http://example.com/geometry");

		for (String functionUri : FUNCTION_URIS) {
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, point, point));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, geometryIri));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, wkt("not geometry")));
		}
	}

	@Test
	public void manifestDefinesExactAritiesAndTypedProviders() {
		for (String functionUri : FUNCTION_URIS) {
			QueryFunctionManifest.Entry entry = QueryFunctionManifest.entries().stream()
					.filter(candidate -> functionUri.equals(candidate.uri()))
					.findFirst()
					.orElseThrow(() -> new AssertionError("Missing manifest entry: " + functionUri));

			assertEquals(functionUri, 1, entry.mandatoryArity());
			assertTrue(functionUri,
					entry.provider() instanceof QueryFunctionManifest.UnaryGeometryToDoubleProvider);
		}
	}

	private Value evaluate(String functionUri, Value... args) throws ValueExprEvaluationException {
		GeoSparqlFunctionRegistration.registerAll();
		Function function = FunctionRegistry.getInstance().get(functionUri)
				.orElseThrow(() -> new AssertionError("Function not registered: " + functionUri));
		return function.evaluate(TRIPLE_SOURCE, args);
	}

	private Literal wkt(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL);
	}

	private void assertDouble(double expected, Value value) {
		assertTrue(value instanceof Literal);
		assertEquals(expected, ((Literal) value).doubleValue(), 0.0);
		assertEquals(XSD.DOUBLE, ((Literal) value).getDatatype());
	}
}
