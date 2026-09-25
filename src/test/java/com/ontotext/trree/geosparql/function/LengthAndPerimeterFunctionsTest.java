package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.IRI;
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

public class LengthAndPerimeterFunctionsTest {
	private static final String GEOF_LENGTH_URI = GeoConstants.NS_GEOF + "length";
	private static final String GEOF_METRIC_LENGTH_URI = GeoConstants.NS_GEOF + "metricLength";
	private static final String GEOF_METRIC_PERIMETER_URI = GeoConstants.NS_GEOF + "metricPerimeter";
	private static final String GEOF_PERIMETER_URI = GeoConstants.NS_GEOF + "perimeter";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String EPSG_4326 = "http://www.opengis.net/def/crs/EPSG/0/4326";
	private static final String DEGREE = "http://www.opengis.net/def/uom/OGC/1.0/degree";
	private static final String KILOMETRE = "http://www.opengis.net/def/uom/OGC/1.0/kilometre";
	private static final String METRE = "http://www.opengis.net/def/uom/OGC/1.0/metre";
	private static final String UNKNOWN_UNIT = "http://example.com/unit/unknown";
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE =
			new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void lengthReturnsProjectedMeasurementInTheRequestedUnit() throws Exception {
		Literal line = wkt("<" + EPSG_32634
				+ "> LINESTRING(500000 4600000,500003 4600004)");

		Value result = evaluate(GEOF_LENGTH_URI, line, VALUE_FACTORY.createIRI(METRE));

		assertTrue(result instanceof Literal);
		assertEquals(5.0, ((Literal) result).doubleValue(), 0.0);
		assertEquals(XSD.DOUBLE, ((Literal) result).getDatatype());
	}

	@Test
	public void metricLengthReturnsMetres() throws Exception {
		Literal line = wkt("<" + EPSG_32634
				+ "> LINESTRING(500000 4600000,500003 4600004)");

		Value result = evaluate(GEOF_METRIC_LENGTH_URI, line);

		assertEquals(5.0, ((Literal) result).doubleValue(), 0.0);
		assertEquals(XSD.DOUBLE, ((Literal) result).getDatatype());
	}

	@Test
	public void perimeterUsesLengthSemanticsIncludingPolygonHoles() throws Exception {
		Literal polygon = wkt("<" + EPSG_32634 + "> POLYGON("
				+ "(500000 4600000,500010 4600000,500010 4600010,"
				+ "500000 4600010,500000 4600000),"
				+ "(500004 4600004,500006 4600004,500006 4600006,"
				+ "500004 4600006,500004 4600004))");

		Value result = evaluate(GEOF_PERIMETER_URI, polygon, VALUE_FACTORY.createIRI(METRE));

		assertEquals(48.0, ((Literal) result).doubleValue(), 0.0);
		assertEquals(XSD.DOUBLE, ((Literal) result).getDatatype());
	}

	@Test
	public void metricPerimeterReturnsMetres() throws Exception {
		Literal polygon = wkt("<" + EPSG_32634 + "> POLYGON(("
				+ "500000 4600000,500010 4600000,500010 4600010,"
				+ "500000 4600010,500000 4600000))");

		Value result = evaluate(GEOF_METRIC_PERIMETER_URI, polygon);

		assertEquals(40.0, ((Literal) result).doubleValue(), 0.0);
		assertEquals(XSD.DOUBLE, ((Literal) result).getDatatype());
	}

	@Test
	public void allFourFunctionsUseTheSameRecursiveComponentValues() throws Exception {
		Literal collection = wkt("<" + EPSG_32634 + "> GEOMETRYCOLLECTION("
				+ "POINT(500000 4600000),"
				+ "MULTILINESTRING((500000 4600000,500003 4600004),"
				+ "(500010 4600010,500013 4600014)),"
				+ "POLYGON((500000 4600000,500010 4600000,500010 4600010,"
				+ "500000 4600010,500000 4600000),"
				+ "(500004 4600004,500006 4600004,500006 4600006,"
				+ "500004 4600006,500004 4600004)),"
				+ "LINESTRING EMPTY)");

		assertDouble(58.0, evaluate(
				GEOF_LENGTH_URI, collection, VALUE_FACTORY.createIRI(METRE)), 0.0);
		assertDouble(58.0, evaluate(GEOF_METRIC_LENGTH_URI, collection), 0.0);
		assertDouble(58.0, evaluate(
				GEOF_PERIMETER_URI, collection, VALUE_FACTORY.createIRI(METRE)), 0.0);
		assertDouble(58.0, evaluate(GEOF_METRIC_PERIMETER_URI, collection), 0.0);
	}

	@Test
	public void geographicFunctionsSumGreatCircleSegmentsBeforeConversion() throws Exception {
		Literal collection = wkt("GEOMETRYCOLLECTION("
				+ "LINESTRING(0 0,1 0),LINESTRING(1 0,1 1))");
		IRI kilometre = VALUE_FACTORY.createIRI(KILOMETRE);

		assertDouble(222.390159, evaluate(GEOF_LENGTH_URI, collection, kilometre), 0.0);
		assertDouble(222390.1594687375,
				evaluate(GEOF_METRIC_LENGTH_URI, collection), 1e-6);
		assertDouble(222.390159, evaluate(GEOF_PERIMETER_URI, collection, kilometre), 0.0);
		assertDouble(222390.1594687375,
				evaluate(GEOF_METRIC_PERIMETER_URI, collection), 1e-6);
	}

	@Test
	public void geographicLengthUsesJenaNormalizedAuthorityAxisOrder() throws Exception {
		Literal line = wkt("<" + EPSG_4326 + "> LINESTRING(0 0,0 1)");

		assertDouble(111195.07973436874,
				evaluate(GEOF_METRIC_LENGTH_URI, line), 1e-6);
		assertDouble(111195.07973436874,
				evaluate(GEOF_METRIC_PERIMETER_URI, line), 1e-6);
	}

	@Test
	public void emptyGeometriesReturnZeroAfterArgumentAndUnitValidation() throws Exception {
		Literal empty = wkt("<" + EPSG_32634 + "> GEOMETRYCOLLECTION("
				+ "POINT EMPTY,LINESTRING EMPTY,POLYGON EMPTY)");

		assertDouble(0.0, evaluate(
				GEOF_LENGTH_URI, empty, VALUE_FACTORY.createIRI(METRE)), 0.0);
		assertDouble(0.0, evaluate(GEOF_METRIC_LENGTH_URI, empty), 0.0);
		assertDouble(0.0, evaluate(
				GEOF_PERIMETER_URI, empty, VALUE_FACTORY.createIRI(METRE)), 0.0);
		assertDouble(0.0, evaluate(GEOF_METRIC_PERIMETER_URI, empty), 0.0);

		for (String functionUri : List.of(GEOF_LENGTH_URI, GEOF_PERIMETER_URI)) {
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(functionUri, empty, VALUE_FACTORY.createIRI(DEGREE)));
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(functionUri, empty, VALUE_FACTORY.createIRI(UNKNOWN_UNIT)));
		}
	}

	@Test
	public void unitFunctionsAcceptIrisAndSimpleAnyUriLiterals() throws Exception {
		Literal line = wkt("<" + EPSG_32634
				+ "> LINESTRING(500000 4600000,500003 4600004)");

		for (String functionUri : List.of(GEOF_LENGTH_URI, GEOF_PERIMETER_URI)) {
			assertDouble(5.0, evaluate(
					functionUri, line, VALUE_FACTORY.createIRI(METRE)), 0.0);
			assertDouble(0.005, evaluate(
					functionUri, line, VALUE_FACTORY.createLiteral(KILOMETRE, XSD.ANYURI)), 0.0);
		}
	}

	@Test
	public void unitFunctionsRejectInvalidRdfTermsAndUnsupportedUnits() {
		Literal line = wkt("<" + EPSG_32634
				+ "> LINESTRING(500000 4600000,500003 4600004)");

		for (String functionUri : List.of(GEOF_LENGTH_URI, GEOF_PERIMETER_URI)) {
			for (Value invalidUnit : List.of(
					VALUE_FACTORY.createLiteral(METRE),
					VALUE_FACTORY.createLiteral(METRE, "en"),
					VALUE_FACTORY.createLiteral(1))) {
				assertThrows(ValueExprEvaluationException.class,
						() -> evaluate(functionUri, line, invalidUnit));
			}
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(functionUri, line, VALUE_FACTORY.createIRI(DEGREE)));
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(functionUri, line, VALUE_FACTORY.createIRI(UNKNOWN_UNIT)));
		}
	}

	@Test
	public void functionsRejectWrongArityWrongTermsAndMalformedGeometry() {
		Literal line = wkt("LINESTRING(0 0,1 1)");
		Literal malformed = wkt("not geometry");
		IRI geometryIri = VALUE_FACTORY.createIRI("http://example.com/geometry");
		IRI metre = VALUE_FACTORY.createIRI(METRE);

		for (String functionUri : List.of(GEOF_LENGTH_URI, GEOF_PERIMETER_URI)) {
			assertThrows(ValueExprEvaluationException.class, () -> evaluate(functionUri, line));
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(functionUri, line, metre, metre));
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(functionUri, geometryIri, metre));
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(functionUri, malformed, metre));
		}
		for (String functionUri : List.of(GEOF_METRIC_LENGTH_URI, GEOF_METRIC_PERIMETER_URI)) {
			assertThrows(ValueExprEvaluationException.class, () -> evaluate(functionUri));
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(functionUri, line, line));
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(functionUri, geometryIri));
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(functionUri, malformed));
		}
	}

	@Test
	public void manifestEntriesDefineExactAritiesAndTypedProviders() {
		assertManifestEntry(GEOF_LENGTH_URI, 2,
				QueryFunctionManifest.UnaryGeometryUnitToDoubleProvider.class);
		assertManifestEntry(GEOF_METRIC_LENGTH_URI, 1,
				QueryFunctionManifest.UnaryGeometryToDoubleProvider.class);
		assertManifestEntry(GEOF_PERIMETER_URI, 2,
				QueryFunctionManifest.UnaryGeometryUnitToDoubleProvider.class);
		assertManifestEntry(GEOF_METRIC_PERIMETER_URI, 1,
				QueryFunctionManifest.UnaryGeometryToDoubleProvider.class);
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

	private void assertDouble(double expected, Value result, double delta) {
		assertTrue(result instanceof Literal);
		assertEquals(expected, ((Literal) result).doubleValue(), delta);
		assertEquals(XSD.DOUBLE, ((Literal) result).getDatatype());
	}

	private void assertManifestEntry(String uri, int mandatoryArity,
			Class<? extends QueryFunctionManifest.Provider> providerType) {
		QueryFunctionManifest.Entry entry = QueryFunctionManifest.entries().stream()
				.filter(candidate -> uri.equals(candidate.uri()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing manifest entry: " + uri));

		assertEquals(mandatoryArity, entry.mandatoryArity());
		assertTrue(providerType.isInstance(entry.provider()));
	}
}
