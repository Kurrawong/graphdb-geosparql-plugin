package com.ontotext.trree.geosparql.jena.query;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.UnitsConversionException;
import org.apache.jena.geosparql.implementation.registry.UnitsURIException;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GeometryLengthTest {
	private static final String EPSG_2227 = "http://www.opengis.net/def/crs/EPSG/0/2227";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";

	@BeforeClass
	public static void initializeJena() {
		JenaGeometryAdapter.initialize();
	}

	@Test
	public void projectedLengthUsesSourceCoordinatesAndConvertsOnce() throws Exception {
		GeometryWrapper line = geometry("<" + EPSG_32634
				+ "> LINESTRING(500000 4600000,500003 4600004)");

		assertEquals(5.0, GeometryLength.calculate(line, Unit_URI.METRE_URL), 0.0);
		assertEquals(0.005, GeometryLength.calculate(line, Unit_URI.KILOMETRE_URL), 0.0);
	}

	@Test
	public void geographicLengthSumsGreatCircleSegmentsInMetres() throws Exception {
		GeometryWrapper line = geometry("LINESTRING(0 0,1 0,1 1)");

		assertEquals(222390.1594687375,
				GeometryLength.calculate(line, Unit_URI.METRE_URL), 1e-6);
	}

	@Test
	public void metricLengthReturnsMetres() throws Exception {
		GeometryWrapper line = geometry("<" + EPSG_32634
				+ "> LINESTRING(500000 4600000,500003 4600004)");

		assertEquals(5.0, GeometryLength.calculateMetric(line), 0.0);
	}

	@Test
	public void geographicLengthRetainsLongGreatCircleSegments() throws Exception {
		GeometryWrapper line = geometry("LINESTRING(0 0,120 0)");

		assertEquals(13343409.56812425,
				GeometryLength.calculateMetric(line), 1e-6);
	}

	@Test
	public void projectedLengthUsesJtsRecursiveComponentSemantics() throws Exception {
		String crs = "<" + EPSG_32634 + "> ";

		assertEquals(0.0, GeometryLength.calculate(
				geometry(crs + "POINT(500000 4600000)"), Unit_URI.METRE_URL), 0.0);
		assertEquals(48.0, GeometryLength.calculate(geometry(crs + "POLYGON("
				+ "(500000 4600000,500010 4600000,500010 4600010,"
				+ "500000 4600010,500000 4600000),"
				+ "(500004 4600004,500006 4600004,500006 4600006,"
				+ "500004 4600006,500004 4600004))"), Unit_URI.METRE_URL), 0.0);
		assertEquals(10.0, GeometryLength.calculate(geometry(crs + "MULTILINESTRING("
				+ "(500000 4600000,500003 4600004),"
				+ "(500010 4600010,500013 4600014))"), Unit_URI.METRE_URL), 0.0);
		assertEquals(5.0, GeometryLength.calculate(geometry(crs + "GEOMETRYCOLLECTION("
				+ "POINT(500000 4600000),"
				+ "LINESTRING(500000 4600000,500003 4600004),"
				+ "LINESTRING EMPTY)"), Unit_URI.METRE_URL), 0.0);
	}

	@Test
	public void projectedNonMetreCrsUsesItsHorizontalAxisUnit() throws Exception {
		GeometryWrapper line = geometry("<" + EPSG_2227
				+ "> LINESTRING(6300000 2000000,6300003 2000004)");

		assertEquals(1.524003,
				GeometryLength.calculate(line, Unit_URI.METRE_URL), 0.0);
	}

	@Test
	public void componentLengthsAreAccumulatedBeforeUnitConversion() throws Exception {
		GeometryWrapper collection = geometry("<" + EPSG_32634 + "> GEOMETRYCOLLECTION("
				+ "LINESTRING(0 0,0.0004 0),"
				+ "LINESTRING(1 0,1.0004 0))");

		assertEquals(0.000001,
				GeometryLength.calculate(collection, Unit_URI.KILOMETRE_URL), 0.0);
	}

	@Test
	public void emptyLengthValidatesTargetUnitsBeforeReturningZero() throws Exception {
		GeometryWrapper empty = geometry("<" + EPSG_32634 + "> GEOMETRYCOLLECTION EMPTY");

		assertEquals(0.0, GeometryLength.calculate(empty, Unit_URI.METRE_URL), 0.0);
		assertEquals(0.0, GeometryLength.calculateMetric(empty), 0.0);
		assertThrows(UnitsConversionException.class,
				() -> GeometryLength.calculate(empty, Unit_URI.DEGREE_URL));
		assertThrows(UnitsURIException.class,
				() -> GeometryLength.calculate(empty, "http://example.com/unit/unknown"));
	}

	private GeometryWrapper geometry(String wkt) {
		return SourceGeometryLiteral.fromWkt(wkt).asGeometryWrapper();
	}
}
