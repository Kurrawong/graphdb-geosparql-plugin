package com.ontotext.trree.geosparql.jena.query;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class WithinDistanceTest {
	private static final String EPSG_2227 = "http://www.opengis.net/def/crs/EPSG/0/2227";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String KILOMETRE = "http://www.opengis.net/def/uom/OGC/1.0/kilometre";
	private static final String METRE = "http://www.opengis.net/def/uom/OGC/1.0/metre";

	@BeforeClass
	public static void initializeJena() {
		JenaGeometryAdapter.initialize();
	}

	@Test
	public void requestedUnitControlsTheInclusiveBoundary() throws Exception {
		GeometryWrapper left = geometry("<" + EPSG_32634 + "> POINT(500000 4600000)");
		GeometryWrapper right = geometry("<" + EPSG_32634 + "> POINT(500003 4600004)");

		assertTrue(WithinDistance.calculate(left, right, 0.005, KILOMETRE));
		assertFalse(WithinDistance.calculate(left, right, 0.004, KILOMETRE));
		assertFalse(WithinDistance.calculate(left, right, -1.0, KILOMETRE));
	}

	@Test
	public void projectedNonMetreCrsUsesTheRequestedLinearUnit() throws Exception {
		GeometryWrapper left = geometry("<" + EPSG_2227 + "> POINT(6300000 2000000)");
		GeometryWrapper right = geometry("<" + EPSG_2227 + "> POINT(6300003 2000004)");

		assertTrue(WithinDistance.calculate(left, right, 2.0, METRE));
		assertFalse(WithinDistance.calculate(left, right, 1.0, METRE));
	}

	@Test
	public void kilometreThresholdPreservesTheExactBoundaryWithoutRoundingOutsidePoints() throws Exception {
		String crs = "<" + EPSG_32634 + "> ";
		GeometryWrapper left = geometry(crs + "POINT(500000 4600000)");

		assertTrue(WithinDistance.calculate(left, geometry(crs + "POINT(500005 4600000)"), 0.005, KILOMETRE));
		assertFalse(WithinDistance.calculate(left, geometry(crs + "POINT(500005.0004 4600000)"), 0.005, KILOMETRE));
	}

	@Test
	public void zeroKilometreThresholdRejectsSmallPositiveDistances() throws Exception {
		String crs = "<" + EPSG_32634 + "> ";
		GeometryWrapper left = geometry(crs + "POINT(500000 4600000)");
		GeometryWrapper right = geometry(crs + "POINT(500000.0004 4600000)");

		assertFalse(WithinDistance.calculate(left, right, 0.0, KILOMETRE));
	}

	@Test
	public void negativeThresholdRemainsNegativeWhenUnitConversionUnderflows() throws Exception {
		GeometryWrapper point = geometry("<" + EPSG_32634 + "> POINT(500000 4600000)");
		String millimetre = "http://www.opengis.net/def/uom/OGC/1.0/millimetre";

		assertFalse(WithinDistance.calculate(point, point, -Double.MIN_VALUE, millimetre));
		assertTrue(WithinDistance.calculate(point, point, 0.0, millimetre));
		assertTrue(WithinDistance.calculate(point, point, -0.0, millimetre));
	}

	@Test
	public void metreThresholdDoesNotRoundDistancesInNonMetreCoordinates() throws Exception {
		String crs = "<" + EPSG_2227 + "> ";
		GeometryWrapper left = geometry(crs + "POINT(6300000 2000000)");
		GeometryWrapper right = geometry(crs + "POINT(6300005 2000000)");

		assertFalse(WithinDistance.calculate(left, right, 1.524003, METRE));
		assertTrue(WithinDistance.calculate(left, right, 1.524004, METRE));
	}

	@Test
	public void geographicThresholdsConvertGradCoordinatesToDegrees() throws Exception {
		String crs = "<http://www.opengis.net/def/crs/EPSG/0/4807> ";
		GeometryWrapper left = geometry(crs + "POINT(49 0)");
		GeometryWrapper right = geometry(crs + "POINT(50 0)");

		assertTrue(WithinDistance.calculate(left, right, 100.076, KILOMETRE));
		assertFalse(WithinDistance.calculate(left, right, 100.075, KILOMETRE));
	}

	@Test
	public void antipodalDistancesCanSatisfyLargeLinearThresholds() throws Exception {
		GeometryWrapper left = geometry("POINT(166.933727 79.175770)");
		GeometryWrapper right = geometry("POINT(-13.066273 -79.175770)");

		assertTrue(WithinDistance.calculate(left, right, 21000.0, KILOMETRE));
		assertFalse(WithinDistance.calculate(left, right, 19000.0, KILOMETRE));
		assertTrue(WithinDistance.calculate(left, right, 21000000.0, METRE));
	}

	@Test
	public void nonFiniteMeasurementsRaiseArithmeticErrors() {
		String crs = "<" + EPSG_32634 + "> ";
		GeometryWrapper left = geometry(crs + "POINT(-1e308 0)");
		GeometryWrapper right = geometry(crs + "POINT(1e308 0)");

		assertThrows(ArithmeticException.class, () -> WithinDistance.calculate(left, right, 1.0, METRE));
	}

	private GeometryWrapper geometry(String wkt) {
		return GeometryWrapper.extract(wkt, WKTDatatype.URI);
	}
}
