package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.relateng.RelateNG;
import org.locationtech.jts.operation.relateng.RelatePredicate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Verifies semantic round trips across the supported geometry serialization formats. */
public class GeometrySerializationRoundTripTest {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
	private static final String EPSG_4326 = "http://www.opengis.net/def/crs/EPSG/0/4326";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String EPSG_4979 = "http://www.opengis.net/def/crs/EPSG/0/4979";
	private static final Literal GML_SF0_PROFILE = VALUE_FACTORY.createLiteral(
			"http://www.opengis.net/def/profile/ogc/2.0/gml-sf0");

	@Test
	public void everySupportedXyGeometryRootRoundTripsAcrossWktGmlAndGeoJson() throws Exception {
		Map<String, String> cases = new LinkedHashMap<>();
		cases.put("Point", "POINT(1 2)");
		cases.put("MultiPoint", "MULTIPOINT((1 2),(3 4))");
		cases.put("LineString", "LINESTRING(1 2,3 4)");
		cases.put("MultiLineString", "MULTILINESTRING((1 2,3 4),(5 6,7 8))");
		cases.put("Polygon", "POLYGON((0 0,2 0,2 2,0 0))");
		cases.put("MultiPolygon", "MULTIPOLYGON(((0 0,2 0,2 2,0 0)))");
		cases.put("GeometryCollection",
				"GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(3 4,5 6))");

		for (Map.Entry<String, String> entry : cases.entrySet()) {
			Literal source = literal(entry.getValue(), GeoConstants.GEO_WKT_LITERAL);
			Literal gml = asGml(source);
			Literal geoJson = convert(GeoConstants.GEOF_AS_GEO_JSON, source);
			List<RoundTrip> roundTrips = List.of(
					new RoundTrip("WKT to GML", GeoConstants.GEO_GML_LITERAL, gml),
					new RoundTrip("WKT to GeoJSON", GeoConstants.GEO_JSON_LITERAL, geoJson),
					new RoundTrip("WKT to GML to WKT", GeoConstants.GEO_WKT_LITERAL,
							convert(GeoConstants.GEOF_AS_WKT, gml)),
					new RoundTrip("WKT to GeoJSON to WKT", GeoConstants.GEO_WKT_LITERAL,
							convert(GeoConstants.GEOF_AS_WKT, geoJson)),
					new RoundTrip("GML to WKT to GML", GeoConstants.GEO_GML_LITERAL,
							asGml(convert(GeoConstants.GEOF_AS_WKT, gml))),
					new RoundTrip("GML to GeoJSON to GML", GeoConstants.GEO_GML_LITERAL,
							asGml(convert(GeoConstants.GEOF_AS_GEO_JSON, gml))),
					new RoundTrip("GeoJSON to WKT to GeoJSON", GeoConstants.GEO_JSON_LITERAL,
							convert(GeoConstants.GEOF_AS_GEO_JSON,
									convert(GeoConstants.GEOF_AS_WKT, geoJson))),
					new RoundTrip("GeoJSON to GML to GeoJSON", GeoConstants.GEO_JSON_LITERAL,
							convert(GeoConstants.GEOF_AS_GEO_JSON, asGml(geoJson))));

			for (RoundTrip roundTrip : roundTrips) {
				String label = entry.getKey() + " " + roundTrip.label();
				assertEquals(label, roundTrip.datatype(), roundTrip.result().getDatatype());
				assertTopologicallyEqual(label, source, roundTrip.result());
			}
		}
	}

	@Test
	public void directWktAndGmlRoundTripsRetainSourceCrsAndAxisSemantics() throws Exception {
		List<Literal> sources = List.of(
				literal("<" + EPSG_4326 + "> LINESTRING(50 10,51 11)",
						GeoConstants.GEO_WKT_LITERAL),
				literal("<" + EPSG_32634 + "> POINT(799997.80 4589779.63)",
						GeoConstants.GEO_WKT_LITERAL),
				literal("<" + EPSG_4979 + "> POINT Z(-27.47 153.03 55)",
						GeoConstants.GEO_WKT_LITERAL));

		for (Literal source : sources) {
			Literal gml = asGml(source);
			Literal wkt = convert(GeoConstants.GEOF_AS_WKT, gml);
			Literal gmlAgain = asGml(wkt);

			assertSameSupportedGmlSemantics(source.toString() + " GML", source, gml);
			assertSameSupportedGmlSemantics(source.toString() + " WKT", source, wkt);
			assertSameSupportedGmlSemantics(source.toString() + " GML again", source, gmlAgain);
		}
	}

	@Test
	public void generatedGeoJsonRoundTripsUseCrs84XySemantics() throws Exception {
		Literal xyzSource = literal("<" + EPSG_4979 + "> POINT Z(-27.47 153.03 55)",
				GeoConstants.GEO_WKT_LITERAL);
		List<GeneratedGeoJsonCase> cases = List.of(
				new GeneratedGeoJsonCase("XYZ WKT", xyzSource, 153.03, -27.47),
				new GeneratedGeoJsonCase("XYZ GML", asGml(xyzSource), 153.03, -27.47),
				new GeneratedGeoJsonCase("XYM WKT",
						literal("POINT M(1 2 9)", GeoConstants.GEO_WKT_LITERAL), 1, 2),
				new GeneratedGeoJsonCase("XYZM WKT",
						literal("POINT ZM(1 2 3 9)", GeoConstants.GEO_WKT_LITERAL), 1, 2));

		for (GeneratedGeoJsonCase geometryCase : cases) {
			Literal geoJson = convert(GeoConstants.GEOF_AS_GEO_JSON, geometryCase.source());
			Literal wkt = convert(GeoConstants.GEOF_AS_WKT, geoJson);
			Literal gml = asGml(geoJson);
			Literal geoJsonFromWkt = convert(GeoConstants.GEOF_AS_GEO_JSON, wkt);
			Literal geoJsonFromGml = convert(GeoConstants.GEOF_AS_GEO_JSON, gml);

			for (Literal result : List.of(geoJson, wkt, gml, geoJsonFromWkt, geoJsonFromGml)) {
				SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
				Geometry geometry = parsed.asGeometryWrapper().getParsingGeometry();
				assertEquals(result.toString(), CRS84, parsed.effectiveCrsUri());
				assertEquals(result.toString(), 2, parsed.asGeometryWrapper().getCoordinateDimension());
				assertTrue(result.toString(), Double.isNaN(geometry.getCoordinate().getZ()));
				assertTrue(result.toString(), Double.isNaN(geometry.getCoordinate().getM()));
				assertEquals(result.toString(), geometryCase.x(), geometry.getCoordinate().x, 1e-8);
				assertEquals(result.toString(), geometryCase.y(), geometry.getCoordinate().y, 1e-8);
				assertTopologicallyEqual(geometryCase.label() + " " + result.getDatatype(),
						geoJson, result);
			}
		}
	}

	@Test
	public void nativeGeoJsonAltitudeSurvivesIdentityAndWktButNotGeneratedGeoJson() throws Exception {
		Literal source = literal("{\"type\":\"Point\",\"coordinates\":[153.03,-27.47,55],"
				+ "\"bbox\":[153.03,-27.47,55,153.03,-27.47,55],\"note\":\"metadata\"}",
				GeoConstants.GEO_JSON_LITERAL);

		Literal identity = convert(GeoConstants.GEOF_AS_GEO_JSON, source);
		Literal wkt = convert(GeoConstants.GEOF_AS_WKT, source);
		Literal generatedGeoJson = convert(GeoConstants.GEOF_AS_GEO_JSON, wkt);

		for (Literal result : List.of(identity, wkt)) {
			SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
			assertEquals(result.toString(), 3, parsed.asGeometryWrapper().getCoordinateDimension());
			assertEquals(result.toString(), 55,
					parsed.asGeometryWrapper().getParsingGeometry().getCoordinate().getZ(), 0.0);
		}
		assertFalse(identity.stringValue().contains("bbox"));
		assertFalse(identity.stringValue().contains("note"));
		assertEquals(2, JenaGeometryAdapter.toSourceGeometryLiteral(generatedGeoJson)
				.asGeometryWrapper().getCoordinateDimension());
		assertTrue(Double.isNaN(JenaGeometryAdapter.toSourceGeometryLiteral(generatedGeoJson)
				.asGeometryWrapper().getParsingGeometry().getCoordinate().getZ()));
		assertThrows(ValueExprEvaluationException.class, () -> asGml(source));
	}

	@Test
	public void typedAndZeroLengthEmptyGeometriesRemainReusable() throws Exception {
		Map<String, String> typedEmpties = new LinkedHashMap<>();
		typedEmpties.put("Point", "{\"type\":\"Point\",\"coordinates\":[]}");
		typedEmpties.put("MultiPoint", "{\"type\":\"MultiPoint\",\"coordinates\":[]}");
		typedEmpties.put("LineString", "{\"type\":\"LineString\",\"coordinates\":[]}");
		typedEmpties.put("MultiLineString",
				"{\"type\":\"MultiLineString\",\"coordinates\":[]}");
		typedEmpties.put("Polygon", "{\"type\":\"Polygon\",\"coordinates\":[]}");
		typedEmpties.put("MultiPolygon", "{\"type\":\"MultiPolygon\",\"coordinates\":[]}");
		typedEmpties.put("GeometryCollection",
				"{\"type\":\"GeometryCollection\",\"geometries\":[]}");

		for (Map.Entry<String, String> entry : typedEmpties.entrySet()) {
			Literal source = literal(entry.getValue(), GeoConstants.GEO_JSON_LITERAL);
			Literal wkt = convert(GeoConstants.GEOF_AS_WKT, source);
			Literal geoJsonAgain = convert(GeoConstants.GEOF_AS_GEO_JSON, wkt);

			assertEmptyType(entry.getKey() + " WKT", entry.getKey(), wkt);
			assertEmptyType(entry.getKey() + " GeoJSON", entry.getKey(), geoJsonAgain);
			assertEquals(entry.getKey() + " GML", "", asGml(source).stringValue());
		}

		Literal zeroLength = literal("", GeoConstants.GEO_JSON_LITERAL);
		SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(zeroLength);
		assertEquals(0, parsed.asGeometryWrapper().getTopologicalDimension());
		assertEquals(2, parsed.asGeometryWrapper().getCoordinateDimension());
		assertEquals(2, parsed.asGeometryWrapper().getSpatialDimension());
		assertEquals("POINT EMPTY",
				convert(GeoConstants.GEOF_AS_WKT, zeroLength).stringValue());
		assertEquals("", asGml(zeroLength).stringValue());
		assertEquals("{\"type\":\"Point\",\"coordinates\":[]}",
				convert(GeoConstants.GEOF_AS_GEO_JSON, zeroLength).stringValue());
		assertEquals(CandidateBoundsKind.EMPTY,
				JenaGeometryAdapter.toIndexGeometry(parsed).candidateBoundsKind());
		assertFalse(JenaGeometryAdapter.toIndexGeometry(parsed).isSpatialCandidate());
	}

	private static Literal asGml(Literal source) throws Exception {
		return (Literal) JenaFunctionEvaluator.evaluate(VALUE_FACTORY,
				GeoConstants.GEOF_AS_GML.stringValue(), source, GML_SF0_PROFILE);
	}

	private static Literal convert(IRI function, Literal source) throws Exception {
		return (Literal) JenaFunctionEvaluator.evaluate(VALUE_FACTORY, function.stringValue(), source);
	}

	private static Literal literal(String lexicalForm, IRI datatype) {
		return VALUE_FACTORY.createLiteral(lexicalForm, datatype);
	}

	private static void assertTopologicallyEqual(String label, Literal expected, Literal actual)
			throws Exception {
		Geometry expectedGeometry = JenaGeometryAdapter.toSourceGeometryLiteral(expected)
				.asGeometryWrapper().getXYGeometry();
		Geometry actualGeometry = JenaGeometryAdapter.toSourceGeometryLiteral(actual)
				.asGeometryWrapper().getXYGeometry();
		assertTrue(label, RelateNG.relate(expectedGeometry, actualGeometry, RelatePredicate.equalsTopo()));
		assertEquals(label, VALUE_FACTORY.createLiteral(true), JenaFunctionEvaluator.evaluate(
				VALUE_FACTORY, GeoConstants.GEOF_SF_INTERSECTS.stringValue(), expected, actual));
	}

	private static void assertSameSupportedGmlSemantics(String label, Literal expected, Literal actual) {
		SourceGeometryLiteral expectedSource = JenaGeometryAdapter.toSourceGeometryLiteral(expected);
		SourceGeometryLiteral actualSource = JenaGeometryAdapter.toSourceGeometryLiteral(actual);
		Geometry expectedGeometry = expectedSource.asGeometryWrapper().getParsingGeometry();
		Geometry actualGeometry = actualSource.asGeometryWrapper().getParsingGeometry();
		assertEquals(label, expectedSource.effectiveCrsUri(), actualSource.effectiveCrsUri());
		assertEquals(label, expectedSource.asGeometryWrapper().getCoordinateDimension(),
				actualSource.asGeometryWrapper().getCoordinateDimension());
		assertEquals(label, expectedSource.asGeometryWrapper().getSpatialDimension(),
				actualSource.asGeometryWrapper().getSpatialDimension());
		assertTrue(label, expectedGeometry.equalsExact(actualGeometry));
		Coordinate[] expectedCoordinates = expectedGeometry.getCoordinates();
		Coordinate[] actualCoordinates = actualGeometry.getCoordinates();
		assertEquals(label, expectedCoordinates.length, actualCoordinates.length);
		for (int index = 0; index < expectedCoordinates.length; index++) {
			assertOrdinateEquals(label + " Z at coordinate " + index,
					expectedCoordinates[index].getZ(), actualCoordinates[index].getZ());
			assertOrdinateEquals(label + " M at coordinate " + index,
					expectedCoordinates[index].getM(), actualCoordinates[index].getM());
		}
	}

	private static void assertOrdinateEquals(String label, double expected, double actual) {
		if (Double.isNaN(expected)) {
			assertTrue(label, Double.isNaN(actual));
		} else {
			assertEquals(label, expected, actual, 0.0);
		}
	}

	private static void assertEmptyType(String label, String geometryType, Literal literal) throws Exception {
		SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(literal);
		assertEquals(label, geometryType, parsed.asGeometryWrapper().getGeometryType());
		assertTrue(label, parsed.asGeometryWrapper().isEmpty());
		assertEquals(label, VALUE_FACTORY.createLiteral(true), JenaFunctionEvaluator.evaluate(
				VALUE_FACTORY, GeoConstants.GEO_IS_EMPTY.stringValue(), literal));
	}

	private record RoundTrip(String label, IRI datatype, Literal result) {
	}

	private record GeneratedGeoJsonCase(String label, Literal source, double x, double y) {
	}
}
