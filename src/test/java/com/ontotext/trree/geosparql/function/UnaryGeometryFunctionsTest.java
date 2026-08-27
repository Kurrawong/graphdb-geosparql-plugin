package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.function.Function;
import org.eclipse.rdf4j.query.algebra.evaluation.function.FunctionRegistry;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UnaryGeometryFunctionsTest {
	private static final String BOUNDARY_URI = GeoConstants.NS_GEOF + "boundary";
	private static final String CENTROID_URI = GeoConstants.NS_GEOF + "centroid";
	private static final String CONVEX_HULL_URI = GeoConstants.NS_GEOF + "convexHull";
	private static final String ENVELOPE_URI = GeoConstants.NS_GEOF + "envelope";
	private static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
	private static final String EPSG_4326 = "http://www.opengis.net/def/crs/EPSG/0/4326";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final List<String> FUNCTION_URIS = List.of(
			BOUNDARY_URI, CENTROID_URI, CONVEX_HULL_URI, ENVELOPE_URI);
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE = new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void centroidUsesOnlyMembersOfTheHighestTopologicalDimension() throws Exception {
		Literal result = (Literal) evaluate(CENTROID_URI,
				wkt("GEOMETRYCOLLECTION(POINT(80 80),LINESTRING(0 0,10 0))"));

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertEquals("POINT(5 0)", result.stringValue());
	}

	@Test
	public void unaryGeometryFunctionsReturnTheirDocumentedWktResults() throws Exception {
		assertGeometryEquivalent("MULTIPOINT((0 0),(2 0))",
				evaluate(BOUNDARY_URI, wkt("LINESTRING(0 0,2 0)")));
		assertGeometryEquivalent("POINT(1 1)",
				evaluate(CENTROID_URI, wkt("POLYGON((0 0,0 2,2 2,2 0,0 0))")));
		assertGeometryEquivalent("POLYGON((0 0,0 2,2 0,0 0))",
				evaluate(CONVEX_HULL_URI, wkt("MULTIPOINT((0 0),(0 2),(2 0),(1 1))")));
		assertGeometryEquivalent("POLYGON((0 0,0 2,2 2,2 0,0 0))",
				evaluate(ENVELOPE_URI, wkt("LINESTRING(0 0,2 2)")));
	}

	@Test
	public void unaryGeometryFunctionsHaveMandatoryUnaryManifestEntries() {
		for (String functionUri : FUNCTION_URIS) {
			QueryFunctionManifest.Entry entry = QueryFunctionManifest.entries().stream()
					.filter(candidate -> functionUri.equals(candidate.uri()))
					.findFirst()
					.orElseThrow(() -> new AssertionError("Missing manifest entry: " + functionUri));

			assertEquals(functionUri, 1, entry.mandatoryArity());
			assertTrue(functionUri,
					entry.provider() instanceof QueryFunctionManifest.UnaryGeometryProvider);
		}
	}

	@Test
	public void unaryGeometryFunctionsEnforceRdfArgumentsAndMandatoryArity() {
		Literal point = wkt("POINT(1 2)");
		Value geometryIri = VALUE_FACTORY.createIRI("http://example.com/geometry");
		Literal unsupportedDatatype = VALUE_FACTORY.createLiteral(
				"POINT(1 2)", VALUE_FACTORY.createIRI("http://example.com/notAGeometryDatatype"));

		for (String functionUri : FUNCTION_URIS) {
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, point, point));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, geometryIri));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, unsupportedDatatype));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, wkt("not geometry")));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri,
							wkt("<http://example.com/crs/unknown> POINT(1 2)")));
		}
	}

	@Test
	public void geometryResultsRetainTheSourceDatatypeAndSrs() throws Exception {
		Literal projectedWkt = wkt("<" + EPSG_32634 + "> LINESTRING(500000 4600000,"
				+ "500000 4600010,500010 4600010)");
		Literal projectedGml = gmlFromWkt(projectedWkt.stringValue());
		Literal geoJson = geoJson("{\"type\":\"LineString\",\"coordinates\":[[0,70],[0,80],"
				+ "[20,80]]}");

		for (String functionUri : FUNCTION_URIS) {
			assertDatatypeAndSrs(functionUri,
					(Literal) evaluate(functionUri, projectedWkt),
					GeoConstants.GEO_WKT_LITERAL, EPSG_32634);
			assertDatatypeAndSrs(functionUri,
					(Literal) evaluate(functionUri, projectedGml),
					GeoConstants.GEO_GML_LITERAL, EPSG_32634);
			assertDatatypeAndSrs(functionUri,
					(Literal) evaluate(functionUri, geoJson),
					GeoConstants.GEO_JSON_LITERAL, CRS84);
		}
	}

	@Test
	public void unaryGeometryFunctionsRetainPrimitiveSpecificEmptyResults() throws Exception {
		assertEmptyGeometry("MultiPoint", evaluate(BOUNDARY_URI, wkt("LINESTRING EMPTY")));
		assertEmptyGeometry("Point", evaluate(CENTROID_URI, wkt("POINT EMPTY")));
		assertEmptyGeometry("GeometryCollection", evaluate(CONVEX_HULL_URI, wkt("POINT EMPTY")));
		assertEmptyGeometry("Point", evaluate(ENVELOPE_URI, wkt("POINT EMPTY")));
	}

	@Test
	public void unaryGeometryFunctionsRetainGmlEmptyCanonicalization() throws Exception {
		Literal emptyGml = VALUE_FACTORY.createLiteral("", GeoConstants.GEO_GML_LITERAL);

		for (String functionUri : FUNCTION_URIS) {
			Literal result = (Literal) evaluate(functionUri, emptyGml);

			assertEquals(functionUri, GeoConstants.GEO_GML_LITERAL, result.getDatatype());
			assertEquals(functionUri, "", result.stringValue());
		}
	}

	@Test
	public void convexHullAndEnvelopeRetainTheirDegenerateResultTypes() throws Exception {
		assertGeometryType("Point", evaluate(CONVEX_HULL_URI, wkt("MULTIPOINT((1 1),(1 1))")));
		assertGeometryType("LineString",
				evaluate(CONVEX_HULL_URI, wkt("MULTIPOINT((0 0),(1 1),(2 2))")));
		assertGeometryType("Polygon",
				evaluate(CONVEX_HULL_URI, wkt("MULTIPOINT((0 0),(0 2),(2 0))")));

		assertGeometryType("Point", evaluate(ENVELOPE_URI, wkt("POINT(1 1)")));
		assertGeometryType("LineString", evaluate(ENVELOPE_URI, wkt("LINESTRING(0 1,2 1)")));
		assertGeometryType("Polygon", evaluate(ENVELOPE_URI, wkt("LINESTRING(0 0,2 2)")));
	}

	@Test
	public void centroidReturnsAnXyResultForZMeasuredAndMixedLayouts() throws Exception {
		for (Literal source : List.of(
				wkt("LINESTRING Z(0 0 9,2 0 7)"),
				wkt("LINESTRING M(0 0 9,2 0 7)"),
				wkt("GEOMETRYCOLLECTION Z(POINT Z(0 2 7),LINESTRING M(0 0 5,2 0 6))"))) {
			Literal result = (Literal) evaluate(CENTROID_URI, source);
			SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result);

			assertEquals(source.toString(), 2,
					parsed.asGeometryWrapper().getCoordinateDimension());
			assertEquals(source.toString(), 2,
					parsed.asGeometryWrapper().getSpatialDimension());
		}
	}

	@Test
	public void genericCollectionsAreEligibleExceptForBoundary() throws Exception {
		Literal collection = wkt("GEOMETRYCOLLECTION(POINT(0 2),LINESTRING(0 0,2 0))");

		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(BOUNDARY_URI, collection));
		assertGeometryEquivalent("POINT(1 0)", evaluate(CENTROID_URI, collection));
		assertGeometryEquivalent("POLYGON((0 0,0 2,2 0,0 0))",
				evaluate(CONVEX_HULL_URI, collection));
		assertGeometryEquivalent("POLYGON((0 0,0 2,2 2,2 0,0 0))",
				evaluate(ENVELOPE_URI, collection));
	}

	@Test
	public void homogeneousMultiGeometriesAreEligible() throws Exception {
		Literal multiLineString = wkt("MULTILINESTRING((0 0,2 0),(0 2,2 2))");

		for (String functionUri : FUNCTION_URIS) {
			assertTrue(functionUri, evaluate(functionUri, multiLineString) instanceof Literal);
		}
	}

	@Test
	public void parseableInvalidGeometriesReachTheSelectedPrimitive() throws Exception {
		Literal overlappingMultiPolygon = wkt("MULTIPOLYGON("
				+ "((0 0,0 2,2 2,2 0,0 0)),"
				+ "((1 1,1 3,3 3,3 1,1 1)))");

		for (String functionUri : FUNCTION_URIS) {
			assertTrue(functionUri,
					evaluate(functionUri, overlappingMultiPolygon) instanceof Literal);
		}
	}

	@Test
	public void centroidRetainsPlanarLongitudeLatitudeBehavior() throws Exception {
		Literal result = (Literal) evaluate(CENTROID_URI,
				wkt("POLYGON((0 70,0 80,20 80,20 70,0 70))"));

		assertGeometryEquivalent("POINT(10 75)", result);
		assertEquals(CRS84,
				JenaGeometryAdapter.toSourceGeometryLiteral(result).effectiveCrsUri());
	}

	@Test
	public void centroidRetainsAuthorityAxisOrderInTheResultLiteral() throws Exception {
		Literal result = (Literal) evaluate(CENTROID_URI,
				wkt("<" + EPSG_4326 + "> POLYGON((10 20,10 22,12 22,12 20,10 20))"));
		SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result);

		assertEquals(EPSG_4326, parsed.effectiveCrsUri());
		assertEquals(11.0, parsed.asGeometryWrapper().getParsingGeometry().getCoordinate().x, 0.0);
		assertEquals(21.0, parsed.asGeometryWrapper().getParsingGeometry().getCoordinate().y, 0.0);
	}

	@Test
	public void polygonBoundaryIsRepresentedAsALineString() throws Exception {
		Literal wktPolygon = wkt("POLYGON((0 0,0 2,2 2,2 0,0 0))");
		Literal gmlPolygon = gmlFromWkt("POLYGON((0 0,0 2,2 2,2 0,0 0))");
		Literal geoJsonPolygon = geoJson("{\"type\":\"Polygon\","
				+ "\"coordinates\":[[[0,0],[0,2],[2,2],[2,0],[0,0]]]}");

		for (Literal polygon : List.of(wktPolygon, gmlPolygon, geoJsonPolygon)) {
			Literal result = (Literal) evaluate(BOUNDARY_URI, polygon);

			assertEquals(polygon.getDatatype(), result.getDatatype());
			assertGeometryType("LineString", result);
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

	private Literal gmlFromWkt(String lexicalForm) {
		SourceGeometryLiteral source = JenaGeometryAdapter.toSourceGeometryLiteral(wkt(lexicalForm));
		return JenaGeometryAdapter.toRdf4jLiteral(VALUE_FACTORY,
				source.asGeometryWrapper(), GeoConstants.GEO_GML_LITERAL);
	}

	private Literal geoJson(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_JSON_LITERAL);
	}

	private void assertDatatypeAndSrs(String message, Literal result,
			org.eclipse.rdf4j.model.IRI datatype, String srsUri) {
		assertEquals(message, datatype, result.getDatatype());
		assertEquals(message, srsUri,
				JenaGeometryAdapter.toSourceGeometryLiteral(result).effectiveCrsUri());
	}

	private void assertEmptyGeometry(String geometryType, Value result) {
		assertTrue(result instanceof Literal);
		SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral((Literal) result);

		assertEquals(geometryType, parsed.asGeometryWrapper().getGeometryType());
		assertTrue(parsed.asGeometryWrapper().isEmpty());
	}

	private void assertGeometryType(String geometryType, Value result) {
		assertTrue(result instanceof Literal);
		assertEquals(geometryType, JenaGeometryAdapter.toSourceGeometryLiteral((Literal) result)
				.asGeometryWrapper().getGeometryType());
	}

	private void assertGeometryEquivalent(String expectedWkt, Value result) throws ParseException {
		assertTrue(result instanceof Literal);
		Geometry expected = new WKTReader().read(expectedWkt);
		Geometry actual = JenaGeometryAdapter.toSourceGeometryLiteral((Literal) result)
				.asGeometryWrapper().getParsingGeometry();

		assertTrue("Expected " + actual + " to equal " + expected, expected.equalsTopo(actual));
	}
}
