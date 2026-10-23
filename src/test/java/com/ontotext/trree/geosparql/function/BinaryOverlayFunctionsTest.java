package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.GeoJsonResultDimensionPolicy;
import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.function.Function;
import org.eclipse.rdf4j.query.algebra.evaluation.function.FunctionRegistry;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BinaryOverlayFunctionsTest {
	private static final String INTERSECTION_URI = GeoConstants.NS_GEOF + "intersection";
	private static final String UNION_URI = GeoConstants.NS_GEOF + "union";
	private static final String DIFFERENCE_URI = GeoConstants.NS_GEOF + "difference";
	private static final String SYM_DIFFERENCE_URI = GeoConstants.NS_GEOF + "symDifference";
	private static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
	private static final String EPSG_3857 = "http://www.opengis.net/def/crs/EPSG/0/3857";
	private static final String EPSG_5800 = "http://www.opengis.net/def/crs/EPSG/0/5800";
	private static final List<String> FUNCTION_URIS = List.of(
			INTERSECTION_URI, UNION_URI, DIFFERENCE_URI, SYM_DIFFERENCE_URI);
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE =
			new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void manifestEntriesDefineExactUrisBinaryArityAndXyPolicy() {
		for (String functionUri : FUNCTION_URIS) {
			QueryFunctionManifest.Entry entry = manifestEntry(functionUri);

			assertEquals(functionUri, entry.uri());
			assertEquals(functionUri, 2, entry.mandatoryArity());
			assertTrue(functionUri,
					entry.provider() instanceof QueryFunctionManifest.BinaryGeometryProvider);
			QueryFunctionManifest.BinaryGeometryProvider provider =
					(QueryFunctionManifest.BinaryGeometryProvider) entry.provider();
			assertEquals(functionUri, GeoJsonResultDimensionPolicy.XY_ONLY,
					provider.geoJsonResultDimensionPolicy());
		}
	}

	@Test
	public void nonemptyGenericCollectionsAreRejectedInBothOperandPositions() {
		Literal collection = wkt("GEOMETRYCOLLECTION(POINT(0 0),LINESTRING(0 0,2 0))");
		Literal polygon = wkt("POLYGON((-1 -1,-1 1,1 1,1 -1,-1 -1))");

		for (String functionUri : FUNCTION_URIS) {
			assertGenericCollectionRejected(functionUri, collection, polygon);
			assertGenericCollectionRejected(functionUri, polygon, collection);
		}
	}

	@Test
	public void geographicOverlaysUsePlanarJenaResults() throws Exception {
		Literal left = wkt("POLYGON((0 0,0 2,2 2,2 0,0 0))");
		Literal right = wkt("POLYGON((1 0,1 2,3 2,3 0,1 0))");

		assertArea(2.0, evaluate(INTERSECTION_URI, left, right));
		assertArea(6.0, evaluate(UNION_URI, left, right));
		assertArea(2.0, evaluate(DIFFERENCE_URI, left, right));
		assertArea(4.0, evaluate(SYM_DIFFERENCE_URI, left, right));
	}

	@Test
	public void mixedCrsOverlaysAlignTheRightOperandAndRetainFirstOperandDatatypeAndCrs() throws Exception {
		Literal geographic = gmlFromWkt("POLYGON((-0.01 -0.01,-0.01 0.01,"
				+ "0.01 0.01,0.01 -0.01,-0.01 -0.01))");
		Literal projected = wkt("<" + EPSG_3857 + "> POLYGON(("
				+ "-556 -556,-556 556,556 556,556 -556,-556 -556))");

		for (String functionUri : FUNCTION_URIS) {
			Literal result = (Literal) evaluate(functionUri, geographic, projected);

			assertDatatypeAndSrs(functionUri, result, GeoConstants.GEO_GML_LITERAL, CRS84);
			assertTrue(functionUri,
					xyGeometry(result).getEnvelopeInternal().getWidth() < 0.03);
		}

		Literal reverseDifference = (Literal) evaluate(DIFFERENCE_URI, projected, geographic);
		assertDatatypeAndSrs(DIFFERENCE_URI, reverseDifference,
				GeoConstants.GEO_WKT_LITERAL, EPSG_3857);
		assertTrue(xyGeometry(reverseDifference).getEnvelopeInternal().getWidth() < 2_500.0);
	}

	@Test
	public void differenceUsesSourceOperandOrder() throws Exception {
		Literal outer = wkt("POLYGON((0 0,0 3,3 3,3 0,0 0))");
		Literal inner = wkt("POLYGON((1 1,1 2,2 2,2 1,1 1))");

		assertArea(8.0, evaluate(DIFFERENCE_URI, outer, inner));
		assertArea(0.0, evaluate(DIFFERENCE_URI, inner, outer));
	}

	@Test
	public void geoJsonOverlaysEmitXyForXyzSources() throws Exception {
		Literal left = geoJson("{\"type\":\"Polygon\",\"coordinates\":[[[0,0,5],[0,2,5],"
				+ "[2,2,5],[2,0,5],[0,0,5]]]}");
		Literal right = geoJson("{\"type\":\"Polygon\",\"coordinates\":[[[1,0,9],[1,2,9],"
				+ "[3,2,9],[3,0,9],[1,0,9]]]}");

		for (String functionUri : FUNCTION_URIS) {
			Literal result = (Literal) evaluate(functionUri, left, right);

			assertDatatypeAndSrs(functionUri, result, GeoConstants.GEO_JSON_LITERAL, CRS84);
			assertTrue(functionUri, !xyGeometry(result).isEmpty());
			assertXyResult(functionUri, result);
		}
	}

	@Test
	public void emptyGeoJsonOverlayResultsAreCanonicalXy() throws Exception {
		Literal polygon = geoJson("{\"type\":\"Polygon\",\"coordinates\":[[[0,0,5],[0,1,5],"
				+ "[1,1,5],[1,0,5],[0,0,5]]]}");
		Literal disjoint = geoJson("{\"type\":\"Polygon\",\"coordinates\":[[[2,2,9],[2,3,9],"
				+ "[3,3,9],[3,2,9],[2,2,9]]]}");
		Literal empty = geoJson("{\"type\":\"Polygon\",\"coordinates\":[]}");

		assertEmptyXy(INTERSECTION_URI, polygon, disjoint);
		assertEmptyXy(UNION_URI, empty, empty);
		assertEmptyXy(DIFFERENCE_URI, polygon, polygon);
		assertEmptyXy(SYM_DIFFERENCE_URI, polygon, polygon);
	}

	@Test
	public void topologicallyEmptyGenericCollectionsUseJenaOverlayShortCircuits() throws Exception {
		Literal emptyCollection = wkt(
				"GEOMETRYCOLLECTION(POINT EMPTY,LINESTRING EMPTY)");
		Literal polygon = wkt("POLYGON((0 0,0 1,1 1,1 0,0 0))");

		assertEmptyType("Point", evaluate(INTERSECTION_URI, emptyCollection, polygon));
		assertEmptyType("Point", evaluate(INTERSECTION_URI, polygon, emptyCollection));
		assertEmptyType("Point", evaluate(DIFFERENCE_URI, emptyCollection, polygon));
		assertArea(1.0, evaluate(DIFFERENCE_URI, polygon, emptyCollection));
		assertArea(1.0, evaluate(UNION_URI, emptyCollection, polygon));
		assertArea(1.0, evaluate(UNION_URI, polygon, emptyCollection));
		assertEmptyType("Point", evaluate(UNION_URI, emptyCollection, emptyCollection));
		assertArea(1.0, evaluate(SYM_DIFFERENCE_URI, emptyCollection, polygon));
		assertArea(1.0, evaluate(SYM_DIFFERENCE_URI, polygon, emptyCollection));
		assertEmptyType("Point", evaluate(SYM_DIFFERENCE_URI, emptyCollection, emptyCollection));
	}

	@Test
	public void homogeneousMultiGeometryLiteralsAreEligible() throws Exception {
		Literal polygons = wkt("MULTIPOLYGON(((0 0,0 1,1 1,1 0,0 0)),"
				+ "((2 0,2 1,3 1,3 0,2 0)))");
		Literal firstPolygon = wkt("POLYGON((0 0,0 1,1 1,1 0,0 0))");

		assertArea(1.0, evaluate(INTERSECTION_URI, polygons, firstPolygon));
		assertArea(2.0, evaluate(UNION_URI, polygons, firstPolygon));
		assertArea(1.0, evaluate(DIFFERENCE_URI, polygons, firstPolygon));
		assertArea(1.0, evaluate(SYM_DIFFERENCE_URI, polygons, firstPolygon));
	}

	@Test
	public void functionsEnforceGeometryArgumentsAndMandatoryArity() {
		Literal polygon = wkt("POLYGON((0 0,0 1,1 1,1 0,0 0))");
		Value geometryIri = VALUE_FACTORY.createIRI("http://example.com/geometry");
		Literal unsupportedDatatype = VALUE_FACTORY.createLiteral(
				"POINT(0 0)", VALUE_FACTORY.createIRI("http://example.com/notGeometry"));

		for (String functionUri : FUNCTION_URIS) {
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, polygon));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, polygon, polygon, polygon));
			assertRejectedArgument(functionUri, geometryIri, polygon);
			assertRejectedArgument(functionUri, polygon, geometryIri);
			assertRejectedArgument(functionUri, unsupportedDatatype, polygon);
			assertRejectedArgument(functionUri, polygon, unsupportedDatatype);
			assertRejectedArgument(functionUri, wkt("not geometry"), polygon);
			assertRejectedArgument(functionUri, polygon, wkt("not geometry"));
		}
	}

	@Test
	public void functionsRejectInvalidGeometriesInEitherOperandPosition() {
		Literal invalid = wkt("POLYGON((2 2,3 3,3 2,2 3,2 2))");
		Literal valid = wkt("POLYGON((2 2,2 3,3 3,3 2,2 2))");

		for (String functionUri : FUNCTION_URIS) {
			assertRejectedArgument(functionUri, invalid, valid);
			assertRejectedArgument(functionUri, valid, invalid);
		}
	}

	@Test
	public void functionsRejectUnsupportedAndOutOfDomainCrsOperands() {
		Literal polygon = wkt("POLYGON((0 0,0 1,1 1,1 0,0 0))");
		Literal unsupported = wkt("<http://example.com/crs/unknown> POINT(0 0)");
		Literal outsideCrsDomain = wkt("POINT(181 0)");

		for (String functionUri : FUNCTION_URIS) {
			assertRejectedArgument(functionUri, unsupported, polygon);
			assertRejectedArgument(functionUri, polygon, unsupported);
			assertRejectedArgument(functionUri, outsideCrsDomain, polygon);
			assertRejectedArgument(functionUri, polygon, outsideCrsDomain);
		}
	}

	@Test
	public void rightOperandTransformationOccursBeforeEmptyOverlayShortCircuits() {
		Literal empty = wkt("POINT EMPTY");
		Literal untransformable = wkt("<" + EPSG_5800 + "> POINT(1 2)");

		for (String functionUri : FUNCTION_URIS) {
			assertRejectedArgument(functionUri, empty, untransformable);
		}
	}

	private void assertGenericCollectionRejected(String functionUri, Literal left, Literal right) {
		ValueExprEvaluationException error = assertThrows(functionUri,
				ValueExprEvaluationException.class, () -> evaluate(functionUri, left, right));
		assertTrue(functionUri, error.getMessage().contains(
				"Nonempty generic GeometryCollection geometry literals are not supported by binary overlays"));
	}

	private void assertRejectedArgument(String functionUri, Value left, Value right) {
		assertThrows(functionUri, ValueExprEvaluationException.class,
				() -> evaluate(functionUri, left, right));
	}

	private void assertArea(double expected, Value result) {
		assertTrue(result instanceof Literal);
		assertEquals(expected, xyGeometry((Literal) result).getArea(), 1e-12);
	}

	private void assertEmptyXy(String functionUri, Literal left, Literal right) throws Exception {
		Literal result = (Literal) evaluate(functionUri, left, right);

		assertTrue(functionUri, xyGeometry(result).isEmpty());
		assertXyResult(functionUri, result);
	}

	private void assertEmptyType(String expectedType, Value result) {
		assertTrue(result instanceof Literal);
		Geometry geometry = xyGeometry((Literal) result);

		assertTrue(geometry.isEmpty());
		assertEquals(expectedType, geometry.getGeometryType());
	}

	private void assertDatatypeAndSrs(String message, Literal result,
			IRI datatype, String srsUri) {
		assertEquals(message, datatype, result.getDatatype());
		assertEquals(message, srsUri,
				JenaGeometryAdapter.toSourceGeometryLiteral(result).effectiveCrsUri());
	}

	private void assertXyResult(String message, Literal result) {
		SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result);

		assertEquals(message, 2, parsed.asGeometryWrapper().getCoordinateDimension());
		assertEquals(message, 2, parsed.asGeometryWrapper().getSpatialDimension());
		for (Coordinate coordinate : parsed.asGeometryWrapper().getParsingGeometry().getCoordinates()) {
			assertTrue(message, Double.isNaN(coordinate.getZ()));
			assertTrue(message, Double.isNaN(coordinate.getM()));
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

	private Geometry xyGeometry(Literal literal) {
		return JenaGeometryAdapter.toSourceGeometryLiteral(literal)
				.asGeometryWrapper().getXYGeometry();
	}

	private QueryFunctionManifest.Entry manifestEntry(String uri) {
		return QueryFunctionManifest.entries().stream()
				.filter(entry -> uri.equals(entry.uri()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing manifest entry: " + uri));
	}
}
