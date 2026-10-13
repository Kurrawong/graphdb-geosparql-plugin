package com.ontotext.trree.geosparql.jena.query;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.UnitsOfMeasure;
import org.apache.jena.geosparql.implementation.UnitsConversionException;
import org.apache.jena.geosparql.implementation.registry.UnitsURIException;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class GeometryDistanceTest {
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String OGC_UNIT = "http://www.opengis.net/def/uom/OGC/1.0/";

	@BeforeClass
	public static void initializeJena() {
		JenaGeometryAdapter.initialize();
	}

	@Test
	public void linearDistanceUsesJenaUnitConversion() throws Exception {
		GeometryWrapper left = geometry("<" + EPSG_32634 + "> POINT(500000 4600000)");
		GeometryWrapper right = geometry("<" + EPSG_32634 + "> POINT(500003 4600004)");

		assertEquals(5.0, GeometryDistance.calculate(left, right, Unit_URI.METRE_URL), 0.0);
		assertEquals(0.005, GeometryDistance.calculate(left, right, Unit_URI.KILOMETRE_URL), 0.0);
	}

	@Test
	public void angularOutputUsesMetreDistanceAndTheMeanEarthRadius() throws Exception {
		GeometryWrapper left = geometry("POINT(0 0)");
		GeometryWrapper right = geometry("POINT(1 0)");

		assertEquals(1.0, GeometryDistance.calculate(left, right, Unit_URI.DEGREE_URL), 1e-12);
		assertEquals(Math.PI / 180.0, GeometryDistance.calculate(left, right, Unit_URI.RADIAN_URL), 1e-12);
	}

	@Test
	public void rawCoordinateDistanceAlignsTheRightCrsAndRemainsPlanar() throws Exception {
		GeometryWrapper left = geometry("POINT(10 50)");
		GeometryWrapper right = geometry("<http://www.opengis.net/def/crs/EPSG/0/4326> POINT(50 11)");

		assertEquals(1.0, GeometryDistance.calculate(left, right, OGC_UNIT + "GridSpacing"), 0.0);
		assertEquals(1.0, GeometryDistance.calculate(left, right, OGC_UNIT + "unity"), 0.0);
	}

	@Test
	public void geographicDistanceConvertsBothGradAxesToDegrees() throws Exception {
		String crs = "<http://www.opengis.net/def/crs/EPSG/0/4807> ";
		GeometryWrapper left = geometry(crs + "POINT(49 0)");

		assertEquals(100075.57176093172, GeometryDistance.calculate(left,
				geometry(crs + "POINT(50 0)"), Unit_URI.METRE_URL), 1e-6);
		assertEquals(71866.54201916947, GeometryDistance.calculate(left,
				geometry(crs + "POINT(49 1)"), Unit_URI.METRE_URL), 1e-6);
		assertEquals(0.9, GeometryDistance.calculate(left,
				geometry(crs + "POINT(50 0)"), Unit_URI.DEGREE_URL), 1e-12);
	}

	@Test
	public void antipodalAndNearAntipodalDistancesRemainFiniteInLinearAndAngularUnits() throws Exception {
		GeometryWrapper left = geometry("POINT(0 -12)");
		for (String wkt : List.of("POINT(180 12)", "POINT(179.999999 12)")) {
			GeometryWrapper right = geometry(wkt);
			double metres = GeometryDistance.calculate(left, right, Unit_URI.METRE_URL);

			assertTrue(wkt, Double.isFinite(metres));
			assertEquals(Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS, metres, 1.0);
			assertEquals(Math.PI * UnitsOfMeasure.EARTH_MEAN_RADIUS / 1000.0,
					GeometryDistance.calculate(left, right, Unit_URI.KILOMETRE_URL), 0.001);
			assertEquals(Math.PI, GeometryDistance.calculate(left, right, Unit_URI.RADIAN_URL), 1e-6);
		}
	}

	@Test
	public void geographicDistanceAlignsAuthorityAxesWithoutChangingSourceCoordinates() throws Exception {
		GeometryWrapper left = geometry("POINT(10 50)");
		GeometryWrapper right = geometry("<http://www.opengis.net/def/crs/EPSG/0/4326> POINT(50 11)");
		var parsingBefore = right.getParsingGeometry().copy();
		var xyBefore = right.getXYGeometry().copy();

		assertEquals(71474.28714697903,
				GeometryDistance.calculate(left, right, Unit_URI.METRE_URL), 1e-6);
		assertTrue(parsingBefore.equalsExact(right.getParsingGeometry()));
		assertTrue(xyBefore.equalsExact(right.getXYGeometry()));
	}

	@Test
	public void geographicNearestPointsRetainWraparoundAndOverlapBehavior() throws Exception {
		assertEquals(222390.1594687375, GeometryDistance.calculate(geometry("POINT(-179 0)"),
				geometry("LINESTRING(179 -1,179 1)"), Unit_URI.METRE_URL), 1e-6);
		assertEquals(0.0, GeometryDistance.calculate(geometry("POINT(0 0)"),
				geometry("LINESTRING(-1 0,1 0)"), Unit_URI.METRE_URL), 0.0);
		assertThrows(UnitsConversionException.class, () -> GeometryDistance.calculate(
				geometry("POINT(0 0)"), geometry("POINT(0 0)"), Unit_URI.GRAD_URL));
	}

	@Test
	public void unknownUnitsRemainErrors() {
		GeometryWrapper point = geometry("<" + EPSG_32634 + "> POINT(500000 4600000)");

		assertThrows(UnitsURIException.class,
				() -> GeometryDistance.calculate(point, point, "http://example.com/unit/unknown"));
	}

	private GeometryWrapper geometry(String wkt) {
		return SourceGeometryLiteral.fromWkt(wkt).asGeometryWrapper();
	}
}
