package com.ontotext.trree.geosparql.jena.query;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.SRSInfo;
import org.apache.jena.geosparql.implementation.UnitsConversionException;
import org.apache.jena.geosparql.implementation.registry.UnitsURIException;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.apache.sis.referencing.CRS;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GeometryAreaTest {
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String EPSG_2227 = "http://www.opengis.net/def/crs/EPSG/0/2227";
	private static final String UNKNOWN_UNIT = "http://example.com/unit/unknown";

	@BeforeClass
	public static void initializeJena() {
		JenaGeometryAdapter.initialize();
	}

	@Test
	public void polygonAreaUsesTheSquaredTargetUnit() {
		GeometryWrapper polygon = geometry("<" + EPSG_32634 + "> POLYGON(("
				+ "500000 4600000,501000 4600000,501000 4601000,"
				+ "500000 4601000,500000 4600000))");

		assertEquals(1.0, GeometryArea.calculate(polygon, Unit_URI.KILOMETRE_URL), 0.0);
	}

	@Test
	public void metricAreaAccumulatesMultiPolygonShellsAndHoles() {
		GeometryWrapper multiPolygon = geometry("<" + EPSG_32634 + "> MULTIPOLYGON("
				+ "((500000 4600000,500010 4600000,500010 4600010,"
				+ "500000 4600010,500000 4600000),"
				+ "(500004 4600004,500006 4600004,500006 4600006,"
				+ "500004 4600006,500004 4600004)),"
				+ "((500020 4600000,500023 4600000,500023 4600004,"
				+ "500020 4600004,500020 4600000)))");

		assertEquals(108.0, GeometryArea.calculateMetric(multiPolygon), 0.0);
	}

	@Test
	public void areaIneligibleGeometryTypesReturnZeroWithoutSourceCrsCalculation() {
		for (String wkt : List.of(
				"POINT(1 1)",
				"LINESTRING(0 0,1 1)",
				"MULTIPOINT((0 0),(1 1))",
				"MULTILINESTRING((0 0,1 1),(2 2,3 3))",
				"GEOMETRYCOLLECTION(POLYGON((0 0,0 1,1 1,1 0,0 0)))")) {
			assertEquals(0.0, GeometryArea.calculateMetric(geometry(wkt)), 0.0);
		}
	}

	@Test
	public void eligibleAreaRejectsDifferentHorizontalAxisScales() throws Exception {
		GeometryWrapper polygon = geometryWithCrs(
				"<" + EPSG_32634 + "> POLYGON((0 0,0 10,10 10,10 0,0 0))",
				CRS.fromWKT("ENGCRS[\"Mixed axis scale\","
						+ "EDATUM[\"Engineering datum\"],CS[Cartesian,2],"
						+ "AXIS[\"x\",east,ORDER[1],LENGTHUNIT[\"metre\",1]],"
						+ "AXIS[\"y\",north,ORDER[2],LENGTHUNIT[\"foot\",0.3048]]]"));

		assertThrows(UnitsConversionException.class,
				() -> GeometryArea.calculateMetric(polygon));
	}

	@Test
	public void projectedNonMetreAreaSquaresJenaConversionFactor() {
		GeometryWrapper polygon = geometry("<" + EPSG_2227 + "> POLYGON(("
				+ "6300000 2000000,6300003 2000000,6300003 2000004,"
				+ "6300000 2000004,6300000 2000000))");

		assertEquals(1.1148437952119998,
				GeometryArea.calculateMetric(polygon), 1e-12);
	}

	@Test
	public void multiPolygonAreaIsAccumulatedBeforeSquaredConversion() {
		GeometryWrapper multiPolygon = geometry("<" + EPSG_32634 + "> MULTIPOLYGON("
				+ "((0 0,0.02 0,0.02 0.02,0 0.02,0 0)),"
				+ "((1 0,1.02 0,1.02 0.02,1 0.02,1 0)))");

		assertEquals(0.0000000008,
				GeometryArea.calculate(multiPolygon, Unit_URI.KILOMETRE_URL), 1e-20);
	}

	@Test
	public void squaredConversionFactorIsAppliedToAccumulatedAreaOnce() {
		GeometryWrapper polygon = geometry("<" + EPSG_32634
				+ "> POLYGON((0 0,0.009 0,0.009 1,0 1,0 0))");

		assertEquals(0.000000009,
				GeometryArea.calculate(polygon, Unit_URI.KILOMETRE_URL), 0.0);
	}

	@Test
	public void yardConversionRetainsJenaVersionBehavior() {
		GeometryWrapper polygon = geometry("<" + EPSG_32634
				+ "> POLYGON((0 0,0 1,1 1,1 0,0 0))");

		assertEquals(0.83612736,
				GeometryArea.calculate(polygon, Unit_URI.YARD_URL), 0.0);
	}

	@Test
	public void compoundCrsUsesItsTwoDimensionalHorizontalAxisUnits() throws Exception {
		CoordinateReferenceSystem compoundCrs = CRS.compound(
				CRS.forCode(EPSG_32634), CRS.forCode("EPSG:5703"));
		GeometryWrapper polygon = geometryWithCrs(
				"<" + EPSG_32634 + "> POLYGON Z ((0 0 5,0 10 5,10 10 5,10 0 5,0 0 5))",
				compoundCrs);

		assertEquals(100.0, GeometryArea.calculateMetric(polygon), 0.0);
	}

	@Test
	public void eligibleGeographicAreaIsRejected() {
		GeometryWrapper polygon = geometry("POLYGON((0 0,0 1,1 1,1 0,0 0))");

		assertThrows(UnitsConversionException.class,
				() -> GeometryArea.calculateMetric(polygon));
	}

	@Test
	public void emptyEligibleAreaSkipsSourceAxisValidation() throws Exception {
		CoordinateReferenceSystem mixedScaleCrs = CRS.fromWKT("ENGCRS[\"Mixed axis scale\","
				+ "EDATUM[\"Engineering datum\"],CS[Cartesian,2],"
				+ "AXIS[\"x\",east,ORDER[1],LENGTHUNIT[\"metre\",1]],"
				+ "AXIS[\"y\",north,ORDER[2],LENGTHUNIT[\"foot\",0.3048]]]");

		assertEquals(0.0, GeometryArea.calculateMetric(geometryWithCrs(
				"<" + EPSG_32634 + "> POLYGON EMPTY", mixedScaleCrs)), 0.0);
		assertEquals(0.0, GeometryArea.calculateMetric(geometryWithCrs(
				"<" + EPSG_32634 + "> MULTIPOLYGON EMPTY", mixedScaleCrs)), 0.0);
	}

	@Test
	public void zeroAreaStillValidatesTargetUnits() {
		for (GeometryWrapper geometry : List.of(
				geometry("POINT(1 1)"),
				geometry("<" + EPSG_32634 + "> POLYGON EMPTY"))) {
			assertThrows(UnitsConversionException.class,
					() -> GeometryArea.calculate(geometry, Unit_URI.DEGREE_URL));
			assertThrows(UnitsURIException.class,
					() -> GeometryArea.calculate(geometry, UNKNOWN_UNIT));
		}
	}

	private GeometryWrapper geometry(String wkt) {
		return SourceGeometryLiteral.fromWkt(wkt).asGeometryWrapper();
	}

	private GeometryWrapper geometryWithCrs(String wkt, CoordinateReferenceSystem crs) {
		return new GeometryWrapperWithSrsInfo(geometry(wkt), new TestSrsInfo(crs));
	}

	private static final class GeometryWrapperWithSrsInfo extends GeometryWrapper {
		private final SRSInfo srsInfo;

		private GeometryWrapperWithSrsInfo(GeometryWrapper geometry, SRSInfo srsInfo) {
			super(geometry);
			this.srsInfo = srsInfo;
		}

		@Override
		public SRSInfo getSrsInfo() {
			return srsInfo;
		}
	}

	private static final class TestSrsInfo extends SRSInfo {
		private final CoordinateReferenceSystem crs;

		private TestSrsInfo(CoordinateReferenceSystem crs) {
			super(EPSG_32634);
			this.crs = crs;
		}

		@Override
		public CoordinateReferenceSystem getCrs() {
			return crs;
		}
	}
}
