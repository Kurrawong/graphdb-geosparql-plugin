package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verifies conversion to reusable, source-CRS-preserving WKT geometry literals. */
public class AsWktFunctionTest {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final String EPSG_4326 = "http://www.opengis.net/def/crs/EPSG/0/4326";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String EPSG_4979 = "http://www.opengis.net/def/crs/EPSG/0/4979";

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
	public void supportedCoordinateLayoutsRetainDimensionMarkersAndOrdinates() throws Exception {
		List<LayoutCase> cases = List.of(
				layout("XY WKT", wkt("POINT(1 2)"), "POINT(", 2, 2, Double.NaN, Double.NaN),
				layout("XYZ WKT", wkt("POINT Z(1 2 3)"), "POINT Z(", 3, 3, 3, Double.NaN),
				layout("XYM WKT", wkt("POINT M(1 2 9)"), "POINT M(", 3, 2, Double.NaN, 9),
				layout("XYZM WKT", wkt("POINT ZM(1 2 3 9)"), "POINT ZM(", 4, 3, 3, 9),
				layout("XYZ GML", gmlFromWkt(wkt(
						"<" + EPSG_4979 + "> POINT Z(-27.47 153.03 55)")),
						"<" + EPSG_4979 + "> POINT Z(", 3, 3, 55, Double.NaN),
				layout("XYZ GeoJSON",
						geoJson("{\"type\":\"Point\",\"coordinates\":[1,2,3]}"),
						"POINT Z(", 3, 3, 3, Double.NaN));

		for (LayoutCase geometryCase : cases) {
			Literal result = evaluate(geometryCase.source());
			SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result);

			assertEquals(geometryCase.label(), GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
			assertTrue(geometryCase.label() + " lexical form: " + result.stringValue(),
					result.stringValue().startsWith(geometryCase.lexicalPrefix()));
			assertEquals(geometryCase.label(), geometryCase.coordinateDimension(),
					parsed.asGeometryWrapper().getCoordinateDimension());
			assertEquals(geometryCase.label(), geometryCase.spatialDimension(),
					parsed.asGeometryWrapper().getSpatialDimension());
			assertOrdinate(geometryCase.label() + " Z", geometryCase.z(),
					parsed.asGeometryWrapper().getParsingGeometry().getCoordinate().getZ());
			assertOrdinate(geometryCase.label() + " M", geometryCase.m(),
					parsed.asGeometryWrapper().getParsingGeometry().getCoordinate().getM());
		}

		Literal geoJsonResult = evaluate(geoJson(
				"{\"type\":\"Point\",\"coordinates\":[1,2,3]}"));
		assertEquals("http://www.opengis.net/def/crs/OGC/1.3/CRS84",
				JenaGeometryAdapter.toSourceGeometryLiteral(geoJsonResult).effectiveCrsUri());
	}

	@Test
	public void conversionRetainsSourceCrsAndAuthorityAxisSemantics() throws Exception {
		List<Literal> sources = List.of(
				wkt("<" + EPSG_4326 + "> LINESTRING(50 10,51 11)"),
				wkt("<" + EPSG_32634 + "> POINT(799997.80 4589779.63)"),
				VALUE_FACTORY.createLiteral(
						"<gml:LineString xmlns:gml=\"http://www.opengis.net/gml/3.2\" "
								+ "srsName=\"EPSG:25832\"><gml:posList>"
								+ "663957.7594407402 5103981.64908889 "
								+ "663955.9156555551 5103991.151674075"
								+ "</gml:posList></gml:LineString>",
						GeoConstants.GEO_GML_LITERAL));

		for (Literal source : sources) {
			Literal result = evaluate(source);
			SourceGeometryLiteral expected = JenaGeometryAdapter.toSourceGeometryLiteral(source);
			SourceGeometryLiteral actual = JenaGeometryAdapter.toSourceGeometryLiteral(result);

			assertEquals(source.toString(), expected.effectiveCrsUri(), actual.effectiveCrsUri());
			assertTrue(source.toString(), expected.asGeometryWrapper().getParsingGeometry()
					.equalsExact(actual.asGeometryWrapper().getParsingGeometry()));
			assertTrue(source.toString(), expected.asGeometryWrapper().getXYGeometry()
					.equalsExact(actual.asGeometryWrapper().getXYGeometry()));
		}
	}

	@Test
	public void emptyGeometriesRemainTypedAndReusable() throws Exception {
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
			assertEmptyResult(entry.getKey() + " WKT", entry.getKey(),
					evaluate(wkt(entry.getValue().wkt())));
			assertEmptyResult(entry.getKey() + " GeoJSON", entry.getKey(),
					evaluate(geoJson(entry.getValue().geoJson())));
		}

		assertEmptyResult("zero-length GeoJSON", "Point", evaluate(geoJson("")));
		assertEmptyResult("zero-length GML", "Point", evaluate(
				VALUE_FACTORY.createLiteral("", GeoConstants.GEO_GML_LITERAL)));
	}

	private static Literal evaluate(Literal source) throws Exception {
		return (Literal) JenaFunctionEvaluator.evaluate(VALUE_FACTORY,
				GeoConstants.GEOF_AS_WKT.stringValue(), source);
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

		assertEquals(label, GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertEquals(label, expected.effectiveCrsUri(), actual.effectiveCrsUri());
		assertEquals(label, expectedGeometry.getGeometryType(), actualGeometry.getGeometryType());
		assertTrue(label, expectedGeometry.equalsExact(actualGeometry));
	}

	private static GeometryCase geometry(String wkt, String geoJson) {
		return new GeometryCase(wkt, geoJson);
	}

	private static LayoutCase layout(String label, Literal source, String lexicalPrefix,
			int coordinateDimension, int spatialDimension, double z, double m) {
		return new LayoutCase(label, source, lexicalPrefix, coordinateDimension, spatialDimension, z, m);
	}

	private static void assertOrdinate(String label, double expected, double actual) {
		if (Double.isNaN(expected)) {
			assertTrue(label, Double.isNaN(actual));
		} else {
			assertEquals(label, expected, actual, 0.0);
		}
	}

	private static void assertEmptyResult(String label, String geometryType, Literal result) throws Exception {
		SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		assertEquals(label, GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertEquals(label, geometryType, parsed.asGeometryWrapper().getGeometryType());
		assertTrue(label, parsed.asGeometryWrapper().isEmpty());
		assertEquals(label, VALUE_FACTORY.createLiteral(true), JenaFunctionEvaluator.evaluate(
				VALUE_FACTORY, GeoConstants.GEO_IS_EMPTY.stringValue(), result));
	}

	private record GeometryCase(String wkt, String geoJson) {
	}

	private record LayoutCase(String label, Literal source, String lexicalPrefix,
			int coordinateDimension, int spatialDimension, double z, double m) {
	}
}
