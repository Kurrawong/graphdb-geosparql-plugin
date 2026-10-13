package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.GeoJsonResultDimensionPolicy;
import com.ontotext.trree.geosparql.jena.GeoSparqlUnits;
import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DistanceAndBufferFunctionsTest {
	private static final String GEOF_BUFFER_URI = GeoConstants.NS_GEOF + "buffer";
	private static final String GEOF_DISTANCE_URI = GeoConstants.NS_GEOF + "distance";
	private static final String GEOF_METRIC_BUFFER_URI = GeoConstants.NS_GEOF + "metricBuffer";
	private static final String GEOF_METRIC_DISTANCE_URI = GeoConstants.NS_GEOF + "metricDistance";
	private static final String GEOF_METRIC_WITHIN_DISTANCE_URI = GeoConstants.NS_GEOF + "metricWithinDistance";
	private static final String GEOF_WITHIN_DISTANCE_URI = GeoConstants.NS_GEOF + "withinDistance";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
	private static final String METRE = "http://www.opengis.net/def/uom/OGC/1.0/metre";
	private static final String KILOMETRE = "http://www.opengis.net/def/uom/OGC/1.0/kilometre";
	private static final String DEGREE = "http://www.opengis.net/def/uom/OGC/1.0/degree";
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE = new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void metricDistanceReturnsProjectedDistanceInMetres() throws Exception {
		Literal left = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal right = wkt("<" + EPSG_32634 + "> POINT(500003 4600004)");

		Value result = evaluate(GEOF_METRIC_DISTANCE_URI, left, right);

		assertTrue(result instanceof Literal);
		assertEquals(5.0, ((Literal) result).doubleValue(), 0.0);
		assertEquals(XSD.DOUBLE, ((Literal) result).getDatatype());
	}

	@Test
	public void metricWithinDistanceIncludesTheBoundary() throws Exception {
		Literal left = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal right = wkt("<" + EPSG_32634 + "> POINT(500003 4600004)");

		Literal atBoundary = (Literal) evaluate(GEOF_METRIC_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(5));
		assertEquals(XSD.BOOLEAN, atBoundary.getDatatype());
		assertTrue(atBoundary.booleanValue());
		assertFalse(((Literal) evaluate(GEOF_METRIC_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(4.999))).booleanValue());
		assertFalse(((Literal) evaluate(GEOF_METRIC_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(-1))).booleanValue());
		assertTrue(((Literal) evaluate(GEOF_METRIC_WITHIN_DISTANCE_URI,
				left, left, VALUE_FACTORY.createLiteral(0))).booleanValue());
	}

	@Test
	public void metricWithinDistanceUsesTheLeftCrsDistanceCalculation() throws Exception {
		Literal geographicLeft = wkt("POINT(0 0)");
		Literal geographicRight = wkt("POINT(1 0)");
		Literal projectedRight = wkt("<" + EPSG_32634 + "> POINT(799997.80 4589779.63)");
		Literal geographicNearProjected = wkt("POINT(24.5887755 41.4035958)");

		assertTrue(((Literal) evaluate(GEOF_METRIC_WITHIN_DISTANCE_URI,
				geographicLeft, geographicRight, VALUE_FACTORY.createLiteral(111196))).booleanValue());
		assertFalse(((Literal) evaluate(GEOF_METRIC_WITHIN_DISTANCE_URI,
				geographicLeft, geographicRight, VALUE_FACTORY.createLiteral(111194))).booleanValue());
		assertTrue(((Literal) evaluate(GEOF_METRIC_WITHIN_DISTANCE_URI,
				geographicNearProjected, projectedRight, VALUE_FACTORY.createLiteral(1))).booleanValue());
	}

	@Test
	public void metricWithinDistanceRejectsInvalidThresholdsAndArity() {
		Literal point = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");

		for (Value threshold : List.of(
				VALUE_FACTORY.createLiteral("5"),
				VALUE_FACTORY.createIRI("http://example.com/distance/5"),
				VALUE_FACTORY.createLiteral(Double.NaN),
				VALUE_FACTORY.createLiteral(Double.POSITIVE_INFINITY),
				VALUE_FACTORY.createLiteral("1e9999", XSD.DECIMAL))) {
			assertThrows(threshold.toString(), ValueExprEvaluationException.class,
					() -> evaluate(GEOF_METRIC_WITHIN_DISTANCE_URI, point, point, threshold));
		}
		assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(GEOF_METRIC_WITHIN_DISTANCE_URI, point, point));
		assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(GEOF_METRIC_WITHIN_DISTANCE_URI,
							point, point, VALUE_FACTORY.createLiteral(1), VALUE_FACTORY.createLiteral(2)));
	}

	@Test
	public void metricWithinDistanceUsesDistanceEmptyGeometryBehavior() throws Exception {
		Literal projectedEmpty = wkt("<" + EPSG_32634 + "> POINT EMPTY");
		Literal projectedPoint = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal geographicEmpty = wkt("POINT EMPTY");
		Literal geographicPoint = wkt("POINT(1 1)");

		assertTrue(((Literal) evaluate(GEOF_METRIC_WITHIN_DISTANCE_URI,
				projectedEmpty, projectedPoint, VALUE_FACTORY.createLiteral(0))).booleanValue());
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(
				GEOF_METRIC_WITHIN_DISTANCE_URI,
				geographicEmpty, geographicPoint, VALUE_FACTORY.createLiteral(0)));
	}

	@Test
	public void withinDistanceConvertsToTheRequestedUnitAndIncludesTheBoundary() throws Exception {
		Literal left = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal right = wkt("<" + EPSG_32634 + "> POINT(500003 4600004)");
		IRI kilometre = VALUE_FACTORY.createIRI(KILOMETRE);

		Literal atBoundary = (Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(0.005), kilometre);
		assertEquals(XSD.BOOLEAN, atBoundary.getDatatype());
		assertTrue(atBoundary.booleanValue());
		assertFalse(((Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(0.004), kilometre)).booleanValue());
		assertFalse(((Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(-1), kilometre)).booleanValue());
		assertTrue(((Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				left, left, VALUE_FACTORY.createLiteral(0), kilometre)).booleanValue());
		assertTrue(((Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(5), VALUE_FACTORY.createLiteral(METRE, XSD.ANYURI)))
				.booleanValue());
	}

	@Test
	public void withinDistanceAcceptsLengthAliasesAndScalesLightYearThresholds() throws Exception {
		Literal left = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal right = wkt("<" + EPSG_32634 + "> POINT(500003 4600004)");
		Literal kilometre = VALUE_FACTORY.createLiteral(GeoSparqlUnits.URI_KILOMETRE.stringValue(), XSD.ANYURI);

		assertTrue(((Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(0.005), kilometre)).booleanValue());
		assertFalse(((Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(0.004), kilometre)).booleanValue());
		assertTrue(((Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(17), GeoSparqlUnits.URI_FOOT)).booleanValue());
		assertFalse(((Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(16), GeoSparqlUnits.URI_FOOT)).booleanValue());
		assertTrue(((Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(6.0 / 9.460528405e15), GeoSparqlUnits.URI_LIGHT_YEAR))
				.booleanValue());
		assertFalse(((Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(4.0 / 9.460528405e15), GeoSparqlUnits.URI_LIGHT_YEAR))
				.booleanValue());
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(GEOF_WITHIN_DISTANCE_URI,
				left, right, VALUE_FACTORY.createLiteral(Double.MAX_VALUE), GeoSparqlUnits.URI_LIGHT_YEAR));
	}

	@Test
	public void withinDistanceUsesTheLeftCrsForMixedCrsInputs() throws Exception {
		Literal geographicLeft = wkt("POINT(24.5887755 41.4035958)");
		Literal projectedRight = wkt("<" + EPSG_32634 + "> POINT(799997.80 4589779.63)");

		assertTrue(((Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				geographicLeft, projectedRight, VALUE_FACTORY.createLiteral(0.001),
				VALUE_FACTORY.createIRI(KILOMETRE))).booleanValue());
	}

	@Test
	public void withinDistanceRejectsInvalidArgumentsAndIncompatibleUnits() {
		Literal point = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		IRI metre = VALUE_FACTORY.createIRI(METRE);
		for (Value threshold : List.of(
				VALUE_FACTORY.createLiteral("5"),
				VALUE_FACTORY.createIRI("http://example.com/distance/5"),
				VALUE_FACTORY.createLiteral(Double.NaN),
				VALUE_FACTORY.createLiteral(Double.POSITIVE_INFINITY),
				VALUE_FACTORY.createLiteral("1e9999", XSD.DECIMAL))) {
			assertThrows(threshold.toString(), ValueExprEvaluationException.class,
					() -> evaluate(GEOF_WITHIN_DISTANCE_URI, point, point, threshold, metre));
		}
		for (Value unit : List.of(
				VALUE_FACTORY.createLiteral(METRE),
				VALUE_FACTORY.createLiteral(METRE, "en"),
				VALUE_FACTORY.createLiteral(1),
				VALUE_FACTORY.createIRI("http://example.com/unit/unknown"),
				VALUE_FACTORY.createIRI(DEGREE))) {
			assertThrows(unit.toString(), ValueExprEvaluationException.class,
					() -> evaluate(GEOF_WITHIN_DISTANCE_URI, point, point,
							VALUE_FACTORY.createLiteral(0), unit));
		}
		assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(GEOF_WITHIN_DISTANCE_URI, point, point, VALUE_FACTORY.createLiteral(5)));
		assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(GEOF_WITHIN_DISTANCE_URI, point, point, VALUE_FACTORY.createLiteral(5),
							metre, metre));
		assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(GEOF_WITHIN_DISTANCE_URI,
							wkt("not geometry"), point, VALUE_FACTORY.createLiteral(5), metre));
		assertThrows(ValueExprEvaluationException.class,
					() -> evaluate(GEOF_WITHIN_DISTANCE_URI,
							wkt("<http://example.com/crs/unknown> POINT(1 2)"), point,
							VALUE_FACTORY.createLiteral(5), metre));
	}

	@Test
	public void withinDistanceUsesDistanceEmptyGeometryBehavior() throws Exception {
		Literal projectedEmpty = wkt("<" + EPSG_32634 + "> POINT EMPTY");
		Literal projectedPoint = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal geographicEmpty = wkt("POINT EMPTY");
		Literal geographicPoint = wkt("POINT(1 1)");
		IRI metre = VALUE_FACTORY.createIRI(METRE);

		assertTrue(((Literal) evaluate(GEOF_WITHIN_DISTANCE_URI,
				projectedEmpty, projectedPoint, VALUE_FACTORY.createLiteral(0), metre)).booleanValue());
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(
				GEOF_WITHIN_DISTANCE_URI, geographicEmpty, geographicPoint,
				VALUE_FACTORY.createLiteral(0), metre));
	}

	@Test
	public void registeredDistancesConvertGeographicGradsToDegrees() throws Exception {
		String crs = "<http://www.opengis.net/def/crs/EPSG/0/4807> ";
		Literal left = wkt(crs + "POINT(49 0)");
		Literal right = wkt(crs + "POINT(50 0)");
		Literal metric = (Literal) evaluate(GEOF_METRIC_DISTANCE_URI, left, right);
		Literal distance = (Literal) evaluate(GEOF_DISTANCE_URI, left, right, GeoSparqlUnits.URI_METRE);

		assertEquals(XSD.DOUBLE, metric.getDatatype());
		assertEquals(XSD.DOUBLE, distance.getDatatype());
		assertEquals(100075.57176093172, metric.doubleValue(), 1e-6);
		assertEquals(metric.doubleValue(), distance.doubleValue(), 0.0);
		assertEquals(100.075572, ((Literal) evaluate(GEOF_DISTANCE_URI,
				left, right, GeoSparqlUnits.URI_KILOMETRE)).doubleValue(), 0.0);
		assertEquals(0.9, ((Literal) evaluate(GEOF_DISTANCE_URI,
				left, right, GeoSparqlUnits.URI_DEGREE)).doubleValue(), 1e-12);
	}

	@Test
	public void registeredDistancesReturnFiniteAntipodalMeasurements() throws Exception {
		Literal left = wkt("POINT(166.933727 79.175770)");
		Literal right = wkt("POINT(-13.066273 -79.175770)");
		Literal metric = (Literal) evaluate(GEOF_METRIC_DISTANCE_URI, left, right);
		Literal distance = (Literal) evaluate(GEOF_DISTANCE_URI, left, right, GeoSparqlUnits.URI_METRE);

		assertEquals(XSD.DOUBLE, metric.getDatatype());
		assertEquals(XSD.DOUBLE, distance.getDatatype());
		assertTrue(Double.isFinite(metric.doubleValue()));
		assertTrue(Double.isFinite(distance.doubleValue()));
		assertEquals(20015114.352186374, metric.doubleValue(), 1.0);
		assertEquals(metric.doubleValue(), distance.doubleValue(), 0.0);
		assertEquals(20015.114352, ((Literal) evaluate(GEOF_DISTANCE_URI,
				left, right, GeoSparqlUnits.URI_KILOMETRE)).doubleValue(), 0.000001);
	}

	@Test
	public void distanceAcceptsSimpleAnyUriUnit() throws Exception {
		Literal left = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal right = wkt("<" + EPSG_32634 + "> POINT(500003 4600004)");
		Literal metre = VALUE_FACTORY.createLiteral(METRE, XSD.ANYURI);

		Value result = evaluate(GEOF_DISTANCE_URI, left, right, metre);

		assertEquals(5.0, ((Literal) result).doubleValue(), 0.0);
		assertEquals(XSD.DOUBLE, ((Literal) result).getDatatype());
	}

	@Test
	public void distanceRetainsTwoArgumentCompatibilityOverload() throws Exception {
		Literal left = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal right = wkt("<" + EPSG_32634 + "> POINT(500003 4600004)");

		Value result = evaluate(GEOF_DISTANCE_URI, left, right);

		assertEquals(5.0, ((Literal) result).doubleValue(), 0.0);
	}

	@Test
	public void metricBufferReturnsProjectedGeometryInTheSourceCrs() throws Exception {
		Literal point = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");

		Literal result = (Literal) evaluate(
				GEOF_METRIC_BUFFER_URI, point, VALUE_FACTORY.createLiteral(1));

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertTrue(result.stringValue().startsWith("<" + EPSG_32634 + "> POLYGON"));
	}

	@Test
	public void bufferAcceptsSimpleAnyUriUnitAndRetainsSourceCrs() throws Exception {
		Literal point = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal metre = VALUE_FACTORY.createLiteral(METRE, XSD.ANYURI);

		Literal result = (Literal) evaluate(
				GEOF_BUFFER_URI, point, VALUE_FACTORY.createLiteral(1), metre);

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertTrue(result.stringValue().startsWith("<" + EPSG_32634 + "> POLYGON"));
	}

	@Test
	public void distanceAndBufferAcceptUnitIrisAndRejectOtherRdfTerms() throws Exception {
		Literal left = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal right = wkt("<" + EPSG_32634 + "> POINT(500003 4600004)");
		IRI metre = VALUE_FACTORY.createIRI(METRE);

		assertEquals(5.0, ((Literal) evaluate(GEOF_DISTANCE_URI, left, right, metre)).doubleValue(), 0.0);
		assertTrue(((Literal) evaluate(GEOF_BUFFER_URI, left,
				VALUE_FACTORY.createLiteral(1), metre)).stringValue().contains("POLYGON"));

		for (Value invalidUnit : List.of(
				VALUE_FACTORY.createLiteral(METRE),
				VALUE_FACTORY.createLiteral(METRE, "en"),
				VALUE_FACTORY.createLiteral(1))) {
			assertThrows(invalidUnit.toString(), ValueExprEvaluationException.class,
					() -> evaluate(GEOF_DISTANCE_URI, left, right, invalidUnit));
			assertThrows(invalidUnit.toString(), ValueExprEvaluationException.class,
					() -> evaluate(GEOF_BUFFER_URI, left, VALUE_FACTORY.createLiteral(1), invalidUnit));
		}
	}

	@Test
	public void bufferAcceptsFiniteSparqlNumericRadii() throws Exception {
		Literal point = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		IRI metre = VALUE_FACTORY.createIRI(METRE);

		for (Literal radius : List.of(
				VALUE_FACTORY.createLiteral(1),
				VALUE_FACTORY.createLiteral(new BigDecimal("1.5")),
				VALUE_FACTORY.createLiteral(1.25f),
				VALUE_FACTORY.createLiteral(1.125d))) {
			Literal result = (Literal) evaluate(GEOF_BUFFER_URI, point, radius, metre);
			assertTrue(radius.toString(), result.stringValue().contains("POLYGON"));
			assertFalse(radius.toString(), result.stringValue().endsWith("EMPTY"));
		}
	}

	@Test
	public void bufferRejectsNonNumericAndNonFiniteRadii() {
		Literal point = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		IRI metre = VALUE_FACTORY.createIRI(METRE);

		for (Value radius : List.of(
				VALUE_FACTORY.createLiteral("1"),
				VALUE_FACTORY.createIRI("http://example.com/radius/1"),
				VALUE_FACTORY.createLiteral(Double.NaN),
				VALUE_FACTORY.createLiteral(Double.POSITIVE_INFINITY),
				VALUE_FACTORY.createLiteral(Float.NEGATIVE_INFINITY),
				VALUE_FACTORY.createLiteral("1e9999", XSD.DECIMAL))) {
			assertThrows(radius.toString(), ValueExprEvaluationException.class,
					() -> evaluate(GEOF_BUFFER_URI, point, radius, metre));
			assertThrows(radius.toString(), ValueExprEvaluationException.class,
					() -> evaluate(GEOF_METRIC_BUFFER_URI, point, radius));
		}
	}

	@Test
	public void distanceFunctionsAlignTheRightGeometryToTheLeftCrs() throws Exception {
		Literal crs84 = wkt("POINT(24.5887755 41.4035958)");
		Literal utm = wkt("<" + EPSG_32634 + "> POINT(799997.80 4589779.63)");

		assertEquals(0.0, ((Literal) evaluate(
				GEOF_DISTANCE_URI, crs84, utm, VALUE_FACTORY.createIRI(METRE))).doubleValue(), 0.2);
		assertEquals(0.0, ((Literal) evaluate(
				GEOF_METRIC_DISTANCE_URI, crs84, utm)).doubleValue(), 0.2);
	}

	@Test
	public void metricDistanceRetainsJenaGeographicApproximation() throws Exception {
		Literal left = wkt("POINT(0 0)");
		Literal right = wkt("POINT(1 0)");

		Literal result = (Literal) evaluate(GEOF_METRIC_DISTANCE_URI, left, right);

		assertEquals(111195.07973436874, result.doubleValue(), 1e-6);
	}

	@Test
	public void distanceFunctionsRetainJenaEmptyBehavior() throws Exception {
		Literal projectedEmpty = wkt("<" + EPSG_32634 + "> POINT EMPTY");
		Literal projectedPoint = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal geographicEmpty = wkt("POINT EMPTY");
		Literal geographicPoint = wkt("POINT(1 1)");

		assertEquals(0.0, ((Literal) evaluate(GEOF_DISTANCE_URI,
				projectedEmpty, projectedPoint, VALUE_FACTORY.createIRI(METRE))).doubleValue(), 0.0);
		assertEquals(0.0, ((Literal) evaluate(GEOF_METRIC_DISTANCE_URI,
				projectedEmpty, projectedPoint)).doubleValue(), 0.0);
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(GEOF_DISTANCE_URI,
				geographicEmpty, geographicPoint, VALUE_FACTORY.createIRI(METRE)));
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(GEOF_METRIC_DISTANCE_URI,
				geographicEmpty, geographicPoint));
	}

	@Test
	public void geographicBufferValidatesUnitsForEveryFiniteRadius() throws Exception {
		Literal point = wkt("POINT(0 0)");
		IRI metre = VALUE_FACTORY.createIRI(METRE);

		for (Literal radius : List.of(
				VALUE_FACTORY.createLiteral(1),
				VALUE_FACTORY.createLiteral(0),
				VALUE_FACTORY.createLiteral(-1))) {
			assertThrows(radius.toString(), ValueExprEvaluationException.class,
					() -> evaluate(GEOF_BUFFER_URI, point, radius, metre));
			assertThrows(radius.toString(), ValueExprEvaluationException.class,
					() -> evaluate(GEOF_METRIC_BUFFER_URI, point, radius));
		}

		Literal angularResult = (Literal) evaluate(GEOF_BUFFER_URI, point,
				VALUE_FACTORY.createLiteral(0.1), VALUE_FACTORY.createIRI(DEGREE));
		assertTrue(angularResult.stringValue().startsWith("POLYGON"));
		assertFalse(angularResult.stringValue().endsWith("EMPTY"));
	}

	@Test
	public void projectedNegativePointBufferReturnsEmptyPolygon() throws Exception {
		Literal point = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");

		Literal result = (Literal) evaluate(
				GEOF_METRIC_BUFFER_URI, point, VALUE_FACTORY.createLiteral(-1));

		assertEquals("<" + EPSG_32634 + "> POLYGON EMPTY", result.stringValue());
	}

	@Test
	public void projectedEmptyBufferRetainsPolygonEmptyResult() throws Exception {
		Literal emptyLine = wkt("<" + EPSG_32634 + "> LINESTRING EMPTY");

		Literal result = (Literal) evaluate(
				GEOF_METRIC_BUFFER_URI, emptyLine, VALUE_FACTORY.createLiteral(1));

		assertEquals("<" + EPSG_32634 + "> POLYGON EMPTY", result.stringValue());
	}

	@Test
	public void distanceAndBufferRejectUnsupportedCrsAndUnknownUnits() {
		Literal unsupported = wkt("<http://example.com/crs/unknown> POINT(1 2)");
		Literal projected = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		IRI unknownUnit = VALUE_FACTORY.createIRI("http://example.com/unit/unknown");

		assertThrows(ValueExprEvaluationException.class, () -> evaluate(
				GEOF_METRIC_DISTANCE_URI, unsupported, projected));
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(
				GEOF_METRIC_BUFFER_URI, unsupported, VALUE_FACTORY.createLiteral(1)));
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(
				GEOF_DISTANCE_URI, projected, projected, unknownUnit));
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(
				GEOF_BUFFER_URI, projected, VALUE_FACTORY.createLiteral(1), unknownUnit));
	}

	@Test
	public void lengthAliasesRetainNativeJenaConversionValues() throws Exception {
		Literal left = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal right = wkt("<" + EPSG_32634 + "> POINT(500003 4600004)");
		Map<IRI, String> aliases = Map.of(
				GeoSparqlUnits.URI_CENTIMETRE, Unit_URI.CENTIMETRE_URL,
				GeoSparqlUnits.URI_KILOMETRE, Unit_URI.KILOMETRE_URL,
				GeoSparqlUnits.URI_MILLIMETRE, Unit_URI.MILLIMETRE_URL,
				GeoSparqlUnits.URI_FOOT, Unit_URI.FOOT_URL,
				GeoSparqlUnits.URI_US_SURVEY_FOOT, Unit_URI.US_SURVEY_FOOT_URL,
				GeoSparqlUnits.URI_INCH, Unit_URI.INCH_URL,
				GeoSparqlUnits.URI_MILE, Unit_URI.MILE_URL,
				GeoSparqlUnits.URI_NAUTICAL_MILE, Unit_URI.NAUTICAL_MILE_URL,
				GeoSparqlUnits.URI_YARD, Unit_URI.YARD_URL);

		for (Map.Entry<IRI, String> alias : aliases.entrySet()) {
			assertEquals(alias.getKey().stringValue(),
					((Literal) evaluate(GEOF_DISTANCE_URI, left, right,
							VALUE_FACTORY.createIRI(alias.getValue()))).doubleValue(),
					((Literal) evaluate(GEOF_DISTANCE_URI, left, right, alias.getKey())).doubleValue(), 0.0);
		}
	}

	@Test
	public void distanceAcceptsLegacyLengthUnits() throws Exception {
		Literal left = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal right = wkt("<" + EPSG_32634 + "> POINT(500003 4600004)");

		assertEquals(0.005, ((Literal) evaluate(GEOF_DISTANCE_URI,
				left, right, GeoSparqlUnits.URI_KILOMETRE)).doubleValue(), 0.0);
		assertEquals(16.404199, ((Literal) evaluate(GEOF_DISTANCE_URI,
				left, right, GeoSparqlUnits.URI_FOOT)).doubleValue(), 0.0);
		assertEquals(5.285117052613511e-16, ((Literal) evaluate(GEOF_DISTANCE_URI,
				left, right, GeoSparqlUnits.URI_LIGHT_YEAR)).doubleValue(), 1e-30);
	}

	@Test
	public void bufferConvertsLegacyLengthUnitsAndAcceptsAnyUriAliases() throws Exception {
		Literal point = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal kilometre = VALUE_FACTORY.createLiteral(GeoSparqlUnits.URI_KILOMETRE.stringValue(), XSD.ANYURI);
		Literal result = (Literal) evaluate(GEOF_BUFFER_URI,
				point, VALUE_FACTORY.createLiteral(1), kilometre);

		assertEquals(EPSG_32634, SourceGeometryLiteral.fromLiteral(result).asGeometryWrapper().getSrsURI());
		assertEquals(501000.0, SourceGeometryLiteral.fromLiteral(result)
				.asGeometryWrapper().getXYGeometry().getEnvelopeInternal().getMaxX(), 0.0);
	}

	@Test
	public void bufferLengthAliasConvertsRadiusToSourceCoordinateUnits() throws Exception {
		Literal point = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal result = (Literal) evaluate(GEOF_BUFFER_URI, point,
				VALUE_FACTORY.createLiteral(1), GeoSparqlUnits.URI_FOOT);

		assertEquals(500000.3048, SourceGeometryLiteral.fromLiteral(result)
				.asGeometryWrapper().getXYGeometry().getEnvelopeInternal().getMaxX(), 0.0);
	}

	@Test
	public void bufferConvertsLightYearRadiiToLinearSourceUnits() throws Exception {
		Literal point = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal radius = VALUE_FACTORY.createLiteral(1.0 / 9.460528405e15);
		Literal result = (Literal) evaluate(GEOF_BUFFER_URI, point, radius, GeoSparqlUnits.URI_LIGHT_YEAR);

		assertEquals(500001.0, SourceGeometryLiteral.fromLiteral(result)
				.asGeometryWrapper().getXYGeometry().getEnvelopeInternal().getMaxX(), 1e-7);
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_BUFFER_URI, point, VALUE_FACTORY.createLiteral(Double.MAX_VALUE),
						GeoSparqlUnits.URI_LIGHT_YEAR));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_BUFFER_URI, wkt("POINT(0 0)"), VALUE_FACTORY.createLiteral(0),
						GeoSparqlUnits.URI_LIGHT_YEAR));
	}

	@Test
	public void rawCoordinateDistanceAlignsTheRightGeometryToTheLeftCrs() throws Exception {
		Literal left = wkt("POINT(10 50)");
		Literal right = wkt("<http://www.opengis.net/def/crs/EPSG/0/4326> POINT(50 11)");

		for (IRI unit : List.of(GeoSparqlUnits.URI_GRID_SPACING, GeoSparqlUnits.URI_UNITY)) {
			assertEquals(1.0, ((Literal) evaluate(GEOF_DISTANCE_URI, left, right, unit)).doubleValue(), 0.0);
		}
	}

	@Test
	public void distanceRetainsAngularAndRawCoordinateOutputs() throws Exception {
		Literal left = wkt("POINT(0 0)");
		Literal right = wkt("POINT(1 0)");

		assertEquals(1.0, ((Literal) evaluate(GEOF_DISTANCE_URI,
				left, right, GeoSparqlUnits.URI_DEGREE)).doubleValue(), 1e-12);
		assertEquals(Math.PI / 180.0, ((Literal) evaluate(GEOF_DISTANCE_URI,
				left, right, GeoSparqlUnits.URI_RADIAN)).doubleValue(), 1e-12);
		for (IRI unit : List.of(GeoSparqlUnits.URI_GRID_SPACING, GeoSparqlUnits.URI_UNITY)) {
			assertEquals(1.0, ((Literal) evaluate(GEOF_DISTANCE_URI, left, right, unit)).doubleValue(), 0.0);
		}
	}

	@Test
	public void distanceAndBufferRejectMalformedGeometryAndWrongTerms() {
		Literal malformed = wkt("not geometry");
		IRI geometryIri = VALUE_FACTORY.createIRI("http://example.com/geometry");
		IRI metre = VALUE_FACTORY.createIRI(METRE);

		for (Value invalidGeometry : List.of(malformed, geometryIri)) {
			assertThrows(ValueExprEvaluationException.class, () -> evaluate(
					GEOF_DISTANCE_URI, invalidGeometry, wkt("POINT(1 2)"), metre));
			assertThrows(ValueExprEvaluationException.class, () -> evaluate(
					GEOF_METRIC_DISTANCE_URI, invalidGeometry, wkt("POINT(1 2)")));
			assertThrows(ValueExprEvaluationException.class, () -> evaluate(
					GEOF_BUFFER_URI, invalidGeometry, VALUE_FACTORY.createLiteral(1), metre));
			assertThrows(ValueExprEvaluationException.class, () -> evaluate(
					GEOF_METRIC_BUFFER_URI, invalidGeometry, VALUE_FACTORY.createLiteral(1)));
		}
	}

	@Test
	public void distanceAndBufferEnforceMandatoryAndCompatibilityArities() {
		Literal point = wkt("POINT(1 2)");
		IRI metre = VALUE_FACTORY.createIRI(METRE);

		assertThrows(ValueExprEvaluationException.class, () -> evaluate(GEOF_DISTANCE_URI, point));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_DISTANCE_URI, point, point, metre, metre));
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(GEOF_METRIC_DISTANCE_URI, point));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOF_METRIC_DISTANCE_URI, point, point, point));

		assertThrows(ValueExprEvaluationException.class, () -> evaluate(GEOF_BUFFER_URI, point));
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(
				GEOF_BUFFER_URI, point, VALUE_FACTORY.createLiteral(1), metre, metre));
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(GEOF_METRIC_BUFFER_URI, point));
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(
				GEOF_METRIC_BUFFER_URI, point, VALUE_FACTORY.createLiteral(1), metre));
	}

	@Test
	public void bufferRetainsTwoArgumentCompatibilityOverload() throws Exception {
		Literal point = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");

		Literal result = (Literal) evaluate(
				GEOF_BUFFER_URI, point, VALUE_FACTORY.createLiteral(1));

		assertTrue(result.stringValue().startsWith("<" + EPSG_32634 + "> POLYGON"));
	}

	@Test
	public void bufferUsesSharedGmlAndGeoJsonResultAdaptation() throws Exception {
		Literal projectedGml = gmlFromWkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal geoJson = VALUE_FACTORY.createLiteral(
				"{\"type\":\"Point\",\"coordinates\":[0,0]}", GeoConstants.GEO_JSON_LITERAL);

		Literal gmlResult = (Literal) evaluate(
				GEOF_METRIC_BUFFER_URI, projectedGml, VALUE_FACTORY.createLiteral(1));
		Literal geoJsonResult = (Literal) evaluate(GEOF_BUFFER_URI, geoJson,
				VALUE_FACTORY.createLiteral(0.1), VALUE_FACTORY.createIRI(DEGREE));

		assertEquals(GeoConstants.GEO_GML_LITERAL, gmlResult.getDatatype());
		assertTrue(gmlResult.stringValue().startsWith("<gml:Polygon"));
		assertEquals(EPSG_32634,
				JenaGeometryAdapter.toSourceGeometryLiteral(gmlResult).effectiveCrsUri());
		assertEquals(GeoConstants.GEO_JSON_LITERAL, geoJsonResult.getDatatype());
		assertEquals(CRS84, JenaGeometryAdapter.toSourceGeometryLiteral(geoJsonResult).effectiveCrsUri());
	}

	@Test
	public void bufferGeoJsonResultsAreXyForXyzSourcesAndEmptyResults() throws Exception {
		Literal source = VALUE_FACTORY.createLiteral(
				"{\"type\":\"Point\",\"coordinates\":[0,0,5]}", GeoConstants.GEO_JSON_LITERAL);

		Literal nonEmpty = (Literal) evaluate(GEOF_BUFFER_URI, source,
				VALUE_FACTORY.createLiteral(0.1), VALUE_FACTORY.createIRI(DEGREE));
		Literal empty = (Literal) evaluate(GEOF_BUFFER_URI, source,
				VALUE_FACTORY.createLiteral(-0.1), VALUE_FACTORY.createIRI(DEGREE));

		assertEquals(2, JenaGeometryAdapter.toSourceGeometryLiteral(nonEmpty)
				.asGeometryWrapper().getCoordinateDimension());
		assertTrue(JenaGeometryAdapter.toSourceGeometryLiteral(empty)
				.asGeometryWrapper().isEmpty());
		assertEquals(2, JenaGeometryAdapter.toSourceGeometryLiteral(empty)
				.asGeometryWrapper().getCoordinateDimension());
	}

	@Test
	public void distanceAndBufferManifestEntriesDefineMandatoryAritiesAndProviders() {
		assertManifestEntry(GEOF_DISTANCE_URI, 3,
				QueryFunctionManifest.BinaryGeometryUnitToDoubleProvider.class);
		assertManifestEntry(GEOF_METRIC_DISTANCE_URI, 2,
				QueryFunctionManifest.BinaryGeometryToDoubleProvider.class);
		assertManifestEntry(GEOF_METRIC_WITHIN_DISTANCE_URI, 3,
				QueryFunctionManifest.BinaryGeometryDoubleToBooleanProvider.class);
		assertManifestEntry(GEOF_WITHIN_DISTANCE_URI, 4,
				QueryFunctionManifest.BinaryGeometryDoubleUnitToBooleanProvider.class);
		assertManifestEntry(GEOF_BUFFER_URI, 3,
				QueryFunctionManifest.UnaryGeometryDoubleUnitToGeometryProvider.class);
		assertManifestEntry(GEOF_METRIC_BUFFER_URI, 2,
				QueryFunctionManifest.UnaryGeometryDoubleToGeometryProvider.class);

		QueryFunctionManifest.UnaryGeometryDoubleUnitToGeometryProvider buffer =
				(QueryFunctionManifest.UnaryGeometryDoubleUnitToGeometryProvider)
						manifestEntry(GEOF_BUFFER_URI).provider();
		QueryFunctionManifest.UnaryGeometryDoubleToGeometryProvider metricBuffer =
				(QueryFunctionManifest.UnaryGeometryDoubleToGeometryProvider)
						manifestEntry(GEOF_METRIC_BUFFER_URI).provider();
		assertEquals(GeoJsonResultDimensionPolicy.XY_ONLY,
				buffer.geoJsonResultDimensionPolicy());
		assertEquals(GeoJsonResultDimensionPolicy.XY_ONLY,
				metricBuffer.geoJsonResultDimensionPolicy());
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

	private Literal gmlFromWkt(String lexicalForm) {
		SourceGeometryLiteral source = JenaGeometryAdapter.toSourceGeometryLiteral(wkt(lexicalForm));
		return JenaGeometryAdapter.toRdf4jLiteral(VALUE_FACTORY,
				source.asGeometryWrapper(), GeoConstants.GEO_GML_LITERAL);
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

	private QueryFunctionManifest.Entry manifestEntry(String uri) {
		return QueryFunctionManifest.entries().stream()
				.filter(candidate -> uri.equals(candidate.uri()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing manifest entry: " + uri));
	}
}
