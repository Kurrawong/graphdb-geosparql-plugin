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

public class AreaFunctionsTest {
	private static final String GEOF_AREA_URI = GeoConstants.NS_GEOF + "area";
	private static final String GEOF_METRIC_AREA_URI = GeoConstants.NS_GEOF + "metricArea";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String DEGREE = "http://www.opengis.net/def/uom/OGC/1.0/degree";
	private static final String KILOMETRE = "http://www.opengis.net/def/uom/OGC/1.0/kilometre";
	private static final String METRE = "http://www.opengis.net/def/uom/OGC/1.0/metre";
	private static final String UNKNOWN_CRS = "http://example.com/crs/unknown";
	private static final String UNKNOWN_UNIT = "http://example.com/unit/unknown";
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE =
			new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void areaReturnsProjectedMeasurementInTheSquaredRequestedUnit() throws Exception {
		Literal polygon = wkt("<" + EPSG_32634 + "> POLYGON(("
				+ "500000 4600000,501000 4600000,501000 4601000,"
				+ "500000 4601000,500000 4600000))");

		Value result = evaluate(
				GEOF_AREA_URI, polygon, VALUE_FACTORY.createIRI(KILOMETRE));

		assertTrue(result instanceof Literal);
		assertEquals(1.0, ((Literal) result).doubleValue(), 0.0);
		assertEquals(XSD.DOUBLE, ((Literal) result).getDatatype());
	}

	@Test
	public void metricAreaReturnsSquareMetres() throws Exception {
		Literal polygon = wkt("<" + EPSG_32634 + "> POLYGON(("
				+ "500000 4600000,500010 4600000,500010 4600010,"
				+ "500000 4600010,500000 4600000))");

		Value result = evaluate(GEOF_METRIC_AREA_URI, polygon);

		assertTrue(result instanceof Literal);
		assertEquals(100.0, ((Literal) result).doubleValue(), 0.0);
		assertEquals(XSD.DOUBLE, ((Literal) result).getDatatype());
	}

	@Test
	public void areaAcceptsIriAndSimpleAnyUriTargetUnits() throws Exception {
		Literal polygon = wkt("<" + EPSG_32634 + "> POLYGON(("
				+ "500000 4600000,500010 4600000,500010 4600010,"
				+ "500000 4600010,500000 4600000))");

		assertDouble(100.0, evaluate(
				GEOF_AREA_URI, polygon, VALUE_FACTORY.createIRI(METRE)), 0.0);
		assertDouble(0.0001, evaluate(GEOF_AREA_URI, polygon,
				VALUE_FACTORY.createLiteral(KILOMETRE, XSD.ANYURI)), 1e-18);
	}

	@Test
	public void areaIneligibleGeometryTypesReturnZero() throws Exception {
		for (String lexicalForm : List.of(
				"POINT(1 1)",
				"LINESTRING(0 0,1 1)",
				"MULTIPOINT((0 0),(1 1))",
				"MULTILINESTRING((0 0,1 1),(2 2,3 3))",
				"GEOMETRYCOLLECTION(POLYGON((0 0,0 1,1 1,1 0,0 0)))")) {
			Literal geometry = wkt(lexicalForm);
			assertDouble(0.0, evaluate(
					GEOF_AREA_URI, geometry, VALUE_FACTORY.createIRI(METRE)), 0.0);
			assertDouble(0.0, evaluate(GEOF_METRIC_AREA_URI, geometry), 0.0);
		}
	}

	@Test
	public void emptyAreaReturnsZeroAfterTargetUnitValidation() throws Exception {
		for (Literal empty : List.of(wkt("POLYGON EMPTY"), wkt("MULTIPOLYGON EMPTY"))) {
			assertDouble(0.0, evaluate(
					GEOF_AREA_URI, empty, VALUE_FACTORY.createIRI(METRE)), 0.0);
			assertDouble(0.0, evaluate(GEOF_METRIC_AREA_URI, empty), 0.0);
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(GEOF_AREA_URI, empty, VALUE_FACTORY.createIRI(DEGREE)));
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(GEOF_AREA_URI, empty, VALUE_FACTORY.createIRI(UNKNOWN_UNIT)));
		}
	}

	@Test
	public void eligibleGeographicAreaIsRejected() {
		Literal polygon = wkt("POLYGON((0 0,0 1,1 1,1 0,0 0))");

		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_AREA_URI, polygon, VALUE_FACTORY.createIRI(METRE)));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_METRIC_AREA_URI, polygon));
	}

	@Test
	public void eligibleAreaRejectsUnsupportedSourceCrs() {
		Literal polygon = wkt("<" + UNKNOWN_CRS
				+ "> POLYGON((0 0,0 1,1 1,1 0,0 0))");

		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_AREA_URI, polygon, VALUE_FACTORY.createIRI(METRE)));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_METRIC_AREA_URI, polygon));
	}

	@Test
	public void areaRejectsInvalidRdfTermsAndUnsupportedUnits() {
		Literal polygon = wkt("<" + EPSG_32634
				+ "> POLYGON((0 0,0 1,1 1,1 0,0 0))");

		for (Value invalidUnit : List.of(
				VALUE_FACTORY.createLiteral(METRE),
				VALUE_FACTORY.createLiteral(METRE, "en"),
				VALUE_FACTORY.createLiteral(1))) {
			assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(GEOF_AREA_URI, polygon, invalidUnit));
		}
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_AREA_URI, polygon, VALUE_FACTORY.createIRI(DEGREE)));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_AREA_URI, polygon, VALUE_FACTORY.createIRI(UNKNOWN_UNIT)));
	}

	@Test
	public void functionsRejectWrongArityWrongTermsAndMalformedGeometry() {
		Literal polygon = wkt("<" + EPSG_32634
				+ "> POLYGON((0 0,0 1,1 1,1 0,0 0))");
		Literal malformed = wkt("not geometry");
		Value geometryIri = VALUE_FACTORY.createIRI("http://example.com/geometry");
		Value metre = VALUE_FACTORY.createIRI(METRE);

		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_AREA_URI, polygon));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_AREA_URI, polygon, metre, metre));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_AREA_URI, geometryIri, metre));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_AREA_URI, malformed, metre));

		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_METRIC_AREA_URI));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_METRIC_AREA_URI, polygon, polygon));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_METRIC_AREA_URI, geometryIri));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_METRIC_AREA_URI, malformed));
	}

	@Test
	public void manifestEntriesDefineExactAritiesAndTypedProviders() {
		assertManifestEntry(GEOF_AREA_URI, 2,
				QueryFunctionManifest.UnaryGeometryUnitToDoubleProvider.class);
		assertManifestEntry(GEOF_METRIC_AREA_URI, 1,
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
