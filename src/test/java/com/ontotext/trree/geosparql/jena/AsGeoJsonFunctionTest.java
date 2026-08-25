package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.geof.nontopological.filter_functions.AsGeoJSONFF;
import org.apache.jena.sparql.expr.NodeValue;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.junit.Test;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Verifies conversion to reusable CRS84 GeoJSON geometry literals. */
public class AsGeoJsonFunctionTest {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
	private static final String EPSG_4326 = "http://www.opengis.net/def/crs/EPSG/0/4326";
	private static final String EPSG_3857 = "http://www.opengis.net/def/crs/EPSG/0/3857";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String EPSG_4979 = "http://www.opengis.net/def/crs/EPSG/0/4979";
	private static final String EPSG_7405 = "http://www.opengis.net/def/crs/EPSG/0/7405";

	@Test
	public void nativeGeoJsonIdentityPreservesAltitudeAndUsesJtsOrdinateFormatting() throws Exception {
		Literal source = geoJson("{\"type\":\"MultiPoint\",\"coordinates\":["
				+ "[0.000123456789,12.123456789,10000000.123456],"
				+ "[-0.000123456789,-12.123456789,-10000000.125]],"
				+ "\"bbox\":[-0.000123456789,-12.123456789,-10000000.125,"
				+ "0.000123456789,12.123456789,10000000.123456],\"description\":\"metadata\"}");

		Literal result = evaluate(source);

		assertEquals(GeoConstants.GEO_JSON_LITERAL, result.getDatatype());
		assertEquals("{\"type\":\"MultiPoint\",\"coordinates\":["
				+ "[1.23456789E-4,12.12345679,1.0000000123456E7],"
				+ "[-1.23456789E-4,-12.12345679,-1.0000000125E7]]}",
				result.stringValue());
	}

	@Test
	public void everySupportedGeometryRootConvertsFromWktGmlAndGeoJson() throws Exception {
		Map<String, GeometryCase> cases = new LinkedHashMap<>();
		cases.put("Point", geometry("POINT(1 2)",
				"{\"type\":\"Point\",\"coordinates\":[1,2]}"));
		cases.put("MultiPoint", geometry("MULTIPOINT((1 2),(3 4))",
				"{\"type\":\"MultiPoint\",\"coordinates\":[[1,2],[3,4]]}"));
		cases.put("LineString", geometry("LINESTRING(1 2,3 4)",
				"{\"type\":\"LineString\",\"coordinates\":[[1,2],[3,4]]}"));
		cases.put("MultiLineString", geometry("MULTILINESTRING((1 2,3 4),(5 6,7 8))",
				"{\"type\":\"MultiLineString\",\"coordinates\":[[[1,2],[3,4]],[[5,6],[7,8]]]}"));
		cases.put("Polygon", geometry("POLYGON((0 0,2 0,2 2,0 0))",
				"{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[2,0],[2,2],[0,0]]]}"));
		cases.put("MultiPolygon", geometry("MULTIPOLYGON(((0 0,2 0,2 2,0 0)))",
				"{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0,0],[2,0],[2,2],[0,0]]]]}"));
		cases.put("GeometryCollection", geometry(
				"GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(3 4,5 6))",
				"{\"type\":\"GeometryCollection\",\"geometries\":["
						+ "{\"type\":\"Point\",\"coordinates\":[1,2]},"
						+ "{\"type\":\"LineString\",\"coordinates\":[[3,4],[5,6]]}]}"));

		for (Map.Entry<String, GeometryCase> entry : cases.entrySet()) {
			String geometryType = entry.getKey();
			GeometryCase geometryCase = entry.getValue();
			Literal wkt = wkt(geometryCase.wkt());
			Literal gml = JenaGeometryAdapter.toRdf4jLiteral(VALUE_FACTORY,
					JenaGeometryAdapter.toSourceGeometryLiteral(wkt).asGeometryWrapper(),
					GeoConstants.GEO_GML_LITERAL);
			Literal geoJson = geoJson(geometryCase.geoJson());

			assertEquivalentResult(geometryType + " WKT", wkt, evaluate(wkt));
			assertEquivalentResult(geometryType + " GML", gml, evaluate(gml));
			assertEquivalentResult(geometryType + " GeoJSON", geoJson, evaluate(geoJson));
		}
	}

	@Test
	public void crsConversionMatchesJenaAndRespectsAuthorityAxisOrder() throws Exception {
		Literal crs84 = wkt("<" + CRS84 + "> POINT(0.000123456789 0.000987654321)");
		Literal projected = wkt("<" + EPSG_32634 + "> POINT(799997.80 4589779.63)");
		Literal authorityAxis = wkt("<" + EPSG_4326 + "> POINT(50 10)");

		Literal crs84Result = evaluate(crs84);
		Literal projectedResult = evaluate(projected);
		Literal authorityAxisResult = evaluate(authorityAxis);

		assertEquals("{\"type\":\"Point\",\"coordinates\":[1.23456789E-4,9.87654321E-4]}",
				crs84Result.stringValue());
		assertPoint(projectedResult, 24.5887755, 41.4035958, 1e-6);
		assertEquals("{\"type\":\"Point\",\"coordinates\":[24.588776,41.403596]}",
				projectedResult.stringValue());
		assertEquals("{\"type\":\"Point\",\"coordinates\":[10,50]}",
				authorityAxisResult.stringValue());
		assertJenaEquivalent(projected, projectedResult, 1e-8);
		assertJenaEquivalent(authorityAxis, authorityAxisResult, 1e-8);
	}

	@Test
	public void projectedGmlConversionMatchesJenaReferenceBehavior() throws Exception {
		Literal source = VALUE_FACTORY.createLiteral(
				"<gml:LineString xmlns:gml=\"http://www.opengis.net/gml/3.2\" srsName=\"EPSG:25832\">"
						+ "<gml:posList>"
						+ "663957.7594407402 5103981.64908889 663955.9156555551 5103991.151674075"
						+ "</gml:posList></gml:LineString>",
				GeoConstants.GEO_GML_LITERAL);

		Literal result = evaluate(source);

		assertJenaEquivalent(source, result, 1e-8);
	}

	@Test
	public void generatedGeoJsonDropsUnverifiedVerticalAndMeasureOrdinates() throws Exception {
		Literal twoDimensionalCrsZ = wkt("POINT Z(1 2 3000)");
		Literal genuinelyThreeDimensionalCrs = wkt(
				"<" + EPSG_4979 + "> POINT Z(-27.47 153.03 55)");
		Literal nonEllipsoidalVerticalCrs = wkt(
				"<" + EPSG_7405 + "> POINT Z(530000 180000 50)");
		List<Literal> sources = List.of(
				twoDimensionalCrsZ,
				gmlFromWkt(twoDimensionalCrsZ),
				genuinelyThreeDimensionalCrs,
				gmlFromWkt(genuinelyThreeDimensionalCrs),
				nonEllipsoidalVerticalCrs,
				gmlFromWkt(nonEllipsoidalVerticalCrs),
				wkt("POINT M(1 2 9)"),
				wkt("POINT ZM(1 2 3 9)"));

		for (Literal source : sources) {
			Literal result = evaluate(source);
			SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
			Geometry geometry = parsed.asGeometryWrapper().getParsingGeometry();

			assertEquals(source.toString(), 2, parsed.asGeometryWrapper().getCoordinateDimension());
			assertTrue(source.toString(), Double.isNaN(geometry.getCoordinate().getZ()));
			assertTrue(source.toString(), Double.isNaN(geometry.getCoordinate().getM()));
		}
	}

	@Test
	public void polygonOutputUsesCounterClockwiseExteriorAndOmitsLegacyCrs() throws Exception {
		Literal clockwise = wkt("POLYGON((0 0,0 2,2 2,2 0,0 0))");

		Literal result = evaluate(clockwise);
		Polygon polygon = (Polygon) JenaGeometryAdapter.toSourceGeometryLiteral(result)
				.asGeometryWrapper().getParsingGeometry();

		assertTrue(Orientation.isCCW(polygon.getExteriorRing().getCoordinateSequence()));
		assertFalse(result.stringValue().contains("\"crs\""));
	}

	@Test
	public void unsupportedCrsAndCoordinateOperationFailureAreExpressionErrors() {
		Literal unsupported = wkt("<http://example.com/crs/unknown> POINT(1 2)");
		Literal outsideOperationDomain = wkt(
				"<" + EPSG_32634 + "> POINT(1E308 1E308)");
		SourceGeometryLiteral recognized = JenaGeometryAdapter.toSourceGeometryLiteral(outsideOperationDomain);

		assertTrue(recognized.asGeometryWrapper().isSRSRecognised());
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(unsupported));
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(outsideOperationDomain));
	}

	@Test
	public void projectedBoundaryCasesRetainJenaVertexTransformationShape() throws Exception {
		Map<String, Literal> sources = new LinkedHashMap<>();
		sources.put("antimeridian", wkt("<" + EPSG_3857 + "> LINESTRING("
				+ "19926188.85 1118889.97,-19926188.85 1118889.97)"));
		sources.put("polar", wkt("<" + EPSG_3857 + "> LINESTRING("
				+ "0 19971868.88,11131949.08 19971868.88)"));
		sources.put("large", wkt("<" + EPSG_3857 + "> POLYGON(("
				+ "-18924313.43 -15538711.1,18924313.43 -15538711.1,"
				+ "18924313.43 15538711.1,-18924313.43 15538711.1,"
				+ "-18924313.43 -15538711.1))"));

		for (Map.Entry<String, Literal> entry : sources.entrySet()) {
			SourceGeometryLiteral source = JenaGeometryAdapter.toSourceGeometryLiteral(entry.getValue());
			Literal result = evaluate(entry.getValue());
			Geometry converted = JenaGeometryAdapter.toSourceGeometryLiteral(result)
					.asGeometryWrapper().getParsingGeometry();

			assertEquals(entry.getKey(), source.asGeometryWrapper().getParsingGeometry().getNumPoints(),
					converted.getNumPoints());
			assertJenaEquivalent(entry.getValue(), result, 1e-8);
		}

		Geometry antimeridian = JenaGeometryAdapter.toSourceGeometryLiteral(evaluate(sources.get("antimeridian")))
				.asGeometryWrapper().getParsingGeometry();
		Geometry polar = JenaGeometryAdapter.toSourceGeometryLiteral(evaluate(sources.get("polar")))
				.asGeometryWrapper().getParsingGeometry();
		assertTrue(antimeridian.getCoordinates()[0].x > 170.0);
		assertTrue(antimeridian.getCoordinates()[1].x < -170.0);
		assertTrue(polar.getCoordinates()[0].y > 80.0);
	}

	@Test
	public void emptySourcesReturnReusableTypedGeoJsonObjects() throws Exception {
		Map<String, GeometryCase> cases = new LinkedHashMap<>();
		cases.put("Point", geometry("POINT EMPTY", "{\"type\":\"Point\",\"coordinates\":[]}"));
		cases.put("MultiPoint", geometry("MULTIPOINT EMPTY",
				"{\"type\":\"MultiPoint\",\"coordinates\":[]}"));
		cases.put("LineString", geometry("LINESTRING EMPTY",
				"{\"type\":\"LineString\",\"coordinates\":[]}"));
		cases.put("MultiLineString", geometry("MULTILINESTRING EMPTY",
				"{\"type\":\"MultiLineString\",\"coordinates\":[]}"));
		cases.put("Polygon", geometry("POLYGON EMPTY", "{\"type\":\"Polygon\",\"coordinates\":[]}"));
		cases.put("MultiPolygon", geometry("MULTIPOLYGON EMPTY",
				"{\"type\":\"MultiPolygon\",\"coordinates\":[]}"));
		cases.put("GeometryCollection", geometry("GEOMETRYCOLLECTION EMPTY",
				"{\"type\":\"GeometryCollection\",\"geometries\":[]}"));

		for (Map.Entry<String, GeometryCase> entry : cases.entrySet()) {
			assertEmptyResult(entry.getKey() + " WKT", entry.getKey(), evaluate(wkt(entry.getValue().wkt())));
			assertEmptyResult(entry.getKey() + " GeoJSON", entry.getKey(),
					evaluate(geoJson(entry.getValue().geoJson())));
		}

		assertEmptyResult("zero-length GeoJSON", "Point", evaluate(geoJson("")));
		assertEmptyResult("zero-length GML", "Point", evaluate(
				VALUE_FACTORY.createLiteral("", GeoConstants.GEO_GML_LITERAL)));
	}

	private static Literal evaluate(Literal source) throws Exception {
		return (Literal) JenaFunctionEvaluator.evaluate(VALUE_FACTORY,
				GeoConstants.GEOF_AS_GEO_JSON.stringValue(), source);
	}

	private static Literal geoJson(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_JSON_LITERAL);
	}

	private static Literal wkt(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL);
	}

	private static Literal gmlFromWkt(Literal wkt) {
		return JenaGeometryAdapter.toRdf4jLiteral(VALUE_FACTORY,
				JenaGeometryAdapter.toSourceGeometryLiteral(wkt).asGeometryWrapper(),
				GeoConstants.GEO_GML_LITERAL);
	}

	private static void assertEquivalentResult(String label, Literal source, Literal result) {
		SourceGeometryLiteral expected = JenaGeometryAdapter.toSourceGeometryLiteral(source);
		SourceGeometryLiteral actual = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		Geometry expectedGeometry = expected.asGeometryWrapper().getParsingGeometry();
		Geometry actualGeometry = actual.asGeometryWrapper().getParsingGeometry();

		assertEquals(label, GeoConstants.GEO_JSON_LITERAL, result.getDatatype());
		assertEquals(label, expectedGeometry.getGeometryType(), actualGeometry.getGeometryType());
		assertTrue(label, expectedGeometry.equalsExact(actualGeometry));
	}

	private static void assertPoint(Literal literal, double x, double y, double tolerance) {
		Geometry geometry = JenaGeometryAdapter.toSourceGeometryLiteral(literal)
				.asGeometryWrapper().getParsingGeometry();
		assertEquals(x, geometry.getCoordinate().x, tolerance);
		assertEquals(y, geometry.getCoordinate().y, tolerance);
	}

	private static void assertJenaEquivalent(Literal source, Literal result, double tolerance) {
		SourceGeometryLiteral sourceGeometry = JenaGeometryAdapter.toSourceGeometryLiteral(source);
		NodeValue jenaResult = new AsGeoJSONFF().exec(NodeValue.makeNode(sourceGeometry.asJenaNode()));
		Geometry expected = JenaGeometryAdapter.toSourceGeometryLiteral(geoJson(jenaResult.asString()))
				.asGeometryWrapper().getParsingGeometry();
		Geometry actual = JenaGeometryAdapter.toSourceGeometryLiteral(result)
				.asGeometryWrapper().getParsingGeometry();
		assertTrue(expected.equalsExact(actual, tolerance));
	}

	private static void assertEmptyResult(String label, String geometryType, Literal result) throws Exception {
		SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		assertEquals(label, geometryType, parsed.asGeometryWrapper().getGeometryType());
		assertTrue(label, parsed.asGeometryWrapper().isEmpty());
		assertEquals(label, VALUE_FACTORY.createLiteral(true), JenaFunctionEvaluator.evaluate(
				VALUE_FACTORY, GeoConstants.GEO_IS_EMPTY.stringValue(), result));
	}

	private static GeometryCase geometry(String wkt, String geoJson) {
		return new GeometryCase(wkt, geoJson);
	}

	private record GeometryCase(String wkt, String geoJson) {
	}
}
