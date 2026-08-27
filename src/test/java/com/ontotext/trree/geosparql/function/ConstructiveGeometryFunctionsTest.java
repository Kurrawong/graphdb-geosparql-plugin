package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.GeoJsonResultDimensionPolicy;
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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ConstructiveGeometryFunctionsTest {
	private static final String BOUNDING_CIRCLE_URI = GeoConstants.NS_GEOF + "boundingCircle";
	private static final String CONCAVE_HULL_URI = GeoConstants.NS_GEOF + "concaveHull";
	private static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final List<String> FUNCTION_URIS = List.of(
			BOUNDING_CIRCLE_URI, CONCAVE_HULL_URI);
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE =
			new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void registeredConcaveHullReturnsTheDocumentedPlanarResult() throws Exception {
		Literal source = wkt("MULTIPOINT((0 0),(0 4),(1 1),(4 0),(4 4),(3 1))");

		Literal result = (Literal) evaluate(CONCAVE_HULL_URI, source);
		Geometry resultGeometry = geometry(result);

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertEquals("Polygon", resultGeometry.getGeometryType());
		assertTrue(resultGeometry.covers(geometry(source)));
		assertTrue(resultGeometry.getArea() < geometry(source).convexHull().getArea());
	}

	@Test
	public void registeredBoundingCircleReturnsTheDocumentedPlanarResult() throws Exception {
		Literal source = wkt("MULTIPOINT((0 0),(2 0))");

		Literal result = (Literal) evaluate(BOUNDING_CIRCLE_URI, source);
		Geometry resultGeometry = geometry(result);

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertEquals("Polygon", resultGeometry.getGeometryType());
		assertEquals(33, resultGeometry.getCoordinates().length);
		assertTrue(resultGeometry.covers(geometry(source)));
	}

	@Test
	public void manifestEntriesDefineExactUrisUnaryArityAndXyPolicy() {
		for (String functionUri : FUNCTION_URIS) {
			QueryFunctionManifest.Entry entry = manifestEntry(functionUri);

			assertEquals(functionUri, entry.uri());
			assertEquals(functionUri, 1, entry.mandatoryArity());
			assertTrue(functionUri,
					entry.provider() instanceof QueryFunctionManifest.UnaryGeometryProvider);
			QueryFunctionManifest.UnaryGeometryProvider provider =
					(QueryFunctionManifest.UnaryGeometryProvider) entry.provider();
			assertEquals(functionUri, GeoJsonResultDimensionPolicy.XY_ONLY,
					provider.geoJsonResultDimensionPolicy());
		}
	}

	@Test
	public void functionsEnforceRdfArgumentsAndMandatoryArity() {
		Literal point = wkt("POINT(1 2)");
		Value geometryIri = VALUE_FACTORY.createIRI("http://example.com/geometry");
		Literal unsupportedDatatype = VALUE_FACTORY.createLiteral(
				"POINT(1 2)", VALUE_FACTORY.createIRI("http://example.com/notGeometry"));

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
	public void resultsRetainTheSourceDatatypeAndSrs() throws Exception {
		Literal projectedWkt = wkt("<" + EPSG_32634 + "> MULTIPOINT("
				+ "(500000 4600000),(500000 4600010),(500010 4600000))");
		Literal projectedGml = gmlFromWkt(projectedWkt.stringValue());
		Literal geoJson = geoJson("{\"type\":\"MultiPoint\",\"coordinates\":["
				+ "[0,70],[0,71],[1,70]]}");

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
	public void functionsReturnPrimitiveSpecificDegenerateAndEmptyResults() throws Exception {
		for (String functionUri : FUNCTION_URIS) {
			Literal emptyWkt = (Literal) evaluate(functionUri, wkt("POINT Z EMPTY"));
			Literal emptyGml = (Literal) evaluate(functionUri,
					VALUE_FACTORY.createLiteral("", GeoConstants.GEO_GML_LITERAL));
			Literal emptyGeoJson = (Literal) evaluate(functionUri,
					geoJson("{\"type\":\"Point\",\"coordinates\":[]}"));

			assertEquals(functionUri, "POLYGON EMPTY", emptyWkt.stringValue());
			assertEquals(functionUri, "", emptyGml.stringValue());
			assertXyResult(functionUri, emptyGeoJson);
			assertTrue(functionUri, geometry(emptyGeoJson).isEmpty());
			assertEquals(functionUri, "Polygon", geometry(emptyGeoJson).getGeometryType());
			assertEquals(functionUri, "Point",
					geometry((Literal) evaluate(functionUri, wkt("POINT Z(1 2 3)")))
							.getGeometryType());
		}

		assertEquals("LineString", geometry((Literal) evaluate(CONCAVE_HULL_URI,
				wkt("MULTIPOINT Z((0 0 5),(1 1 6),(2 2 7))"))).getGeometryType());
		assertEquals("Point", geometry((Literal) evaluate(BOUNDING_CIRCLE_URI,
				wkt("MULTIPOINT Z((1 2 3),(1 2 9))"))).getGeometryType());
	}

	@Test
	public void calculatedResultsAreXyForXyzAndMixedLayoutInputs() throws Exception {
		Literal xyzGeoJson = geoJson("{\"type\":\"MultiPoint\",\"coordinates\":["
				+ "[0,0,5],[0,2,6],[2,0,7],[1,1,8]]}");
		Literal mixedWkt = wkt("GEOMETRYCOLLECTION Z("
				+ "POINT Z(0 2 7),LINESTRING M(0 0 5,2 0 6),POINT Z(2 2 9))");

		for (String functionUri : FUNCTION_URIS) {
			assertXyResult(functionUri, (Literal) evaluate(functionUri, xyzGeoJson));
			assertXyResult(functionUri, (Literal) evaluate(functionUri, mixedWkt));
		}
	}

	@Test
	public void genericCollectionsAndInvalidGeometryReachTheSelectedPrimitive() throws Exception {
		Literal collection = wkt("GEOMETRYCOLLECTION("
				+ "POINT(0 0),LINESTRING(0 2,2 0),POINT(2 2))");
		Literal invalidPolygon = wkt("POLYGON((0 0,2 2,0 2,2 0,0 0))");

		for (String functionUri : FUNCTION_URIS) {
			assertTrue(functionUri, evaluate(functionUri, collection) instanceof Literal);
			assertTrue(functionUri, evaluate(functionUri, invalidPolygon) instanceof Literal);
		}
	}

	@Test
	public void geographicCalculationsArePlanarAndResultsMustRemainInTheCrsDomain() throws Exception {
		Literal boundingCircle = (Literal) evaluate(BOUNDING_CIRCLE_URI,
				wkt("MULTIPOINT((0 80),(2 80))"));
		Literal concaveHull = (Literal) evaluate(CONCAVE_HULL_URI,
				wkt("MULTIPOINT((0 70),(0 74),(1 71),(4 70),(4 74),(3 71))"));
		Coordinate first = geometry(boundingCircle).getCoordinate();

		assertTrue(first.x > 2.0);
		assertEquals(80.0, first.y, 0.0);
		assertEquals(7.0, geometry(concaveHull).getArea(), 0.0);
		assertEquals(CRS84,
				JenaGeometryAdapter.toSourceGeometryLiteral(boundingCircle).effectiveCrsUri());
		assertEquals(CRS84,
				JenaGeometryAdapter.toSourceGeometryLiteral(concaveHull).effectiveCrsUri());
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(BOUNDING_CIRCLE_URI,
						wkt("MULTIPOINT((0 89),(10 89))")));
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

	private Geometry geometry(Literal literal) {
		return JenaGeometryAdapter.toSourceGeometryLiteral(literal)
				.asGeometryWrapper().getParsingGeometry();
	}

	private QueryFunctionManifest.Entry manifestEntry(String uri) {
		return QueryFunctionManifest.entries().stream()
				.filter(entry -> uri.equals(entry.uri()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing manifest entry: " + uri));
	}

	private void assertDatatypeAndSrs(String message, Literal result,
			org.eclipse.rdf4j.model.IRI datatype, String srsUri) {
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
}
