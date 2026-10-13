package com.ontotext.trree.geosparql.jena.query;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.registry.UnitsURIException;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
	public void unknownUnitsRemainErrors() {
		GeometryWrapper point = geometry("<" + EPSG_32634 + "> POINT(500000 4600000)");

		assertThrows(UnitsURIException.class,
				() -> GeometryDistance.calculate(point, point, "http://example.com/unit/unknown"));
	}

	private GeometryWrapper geometry(String wkt) {
		return SourceGeometryLiteral.fromWkt(wkt).asGeometryWrapper();
	}
}
