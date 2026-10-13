package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.junit.Test;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GeometrySimplifyTest {
	@Test
	public void emptyPointRemainsEmptyAtZeroTolerance() {
		GeometryWrapper source = GeometryWrapper.extract("POINT EMPTY", WKTDatatype.URI);

		GeometryWrapper result = GeometrySimplify.calculate(source, 0);

		assertTrue(result.isEmpty());
		assertEquals("Point", result.getGeometryType());
		assertEquals(source.getSrsURI(), result.getSrsURI());
	}

	@Test
	public void zeroToleranceRetainsPointMeasure() {
		GeometryWrapper source = GeometryWrapper.extract("POINT M(1 2 7)", WKTDatatype.URI);

		GeometryWrapper result = GeometrySimplify.calculate(source, 0);
		Point point = (Point) result.getParsingGeometry();

		assertEquals(3, result.getCoordinateDimension());
		assertEquals(1, point.getCoordinateSequence().getMeasures());
		assertEquals(7.0, point.getCoordinateSequence().getM(0), 0.0);
	}

	@Test
	public void survivingLineVerticesRetainAltitudeAndMeasureInSourceAxisOrder() {
		for (String crs : new String[]{"", "<http://www.opengis.net/def/crs/EPSG/0/4326> "}) {
			GeometryWrapper source = GeometryWrapper.extract(crs
					+ "LINESTRING ZM(10 20 3 7,10.1 21 4 8,10 22 5 9)", WKTDatatype.URI);
			for (double tolerance : new double[]{0, 0.2}) {
				GeometryWrapper result = GeometrySimplify.calculate(source, tolerance);
				LineString line = (LineString) result.getParsingGeometry();

				assertEquals(source.getSrsURI(), result.getSrsURI());
				assertEquals(4, result.getCoordinateDimension());
				assertEquals(1, line.getCoordinateSequence().getMeasures());
				assertEquals(tolerance == 0 ? 3 : 2, line.getNumPoints());
				for (int index = 0; index < line.getNumPoints(); index++) {
					int sourceIndex = tolerance == 0 ? index : index * 2;
					assertEquals(sourceIndex == 1 ? 10.1 : 10.0,
							line.getCoordinateSequence().getX(index), 0.0);
					assertEquals(20.0 + sourceIndex, line.getCoordinateSequence().getY(index), 0.0);
					assertEquals(3.0 + sourceIndex, line.getCoordinateSequence().getZ(index), 0.0);
					assertEquals(7.0 + sourceIndex, line.getCoordinateSequence().getM(index), 0.0);
				}
			}
		}
	}

	@Test
	public void authorityAxisPointRetainsAltitudeAtZeroTolerance() {
		GeometryWrapper source = GeometryWrapper.extract(
				"<http://www.opengis.net/def/crs/EPSG/0/4326> POINT Z(10 100 3)", WKTDatatype.URI);

		GeometryWrapper result = GeometrySimplify.calculate(source, 0);
		Point point = (Point) result.getParsingGeometry();

		assertEquals(3, result.getCoordinateDimension());
		assertEquals(10.0, point.getX(), 0.0);
		assertEquals(100.0, point.getY(), 0.0);
		assertEquals(3.0, point.getCoordinateSequence().getZ(0), 0.0);
	}

	@Test
	public void polygonRepairCannotInventAltitudeOrMeasure() {
		GeometryWrapper source = GeometryWrapper.extract(
				"POLYGON ZM((0 0 1 10,2 2 2 20,0 2 3 30,2 0 4 40,0 0 1 10))", WKTDatatype.URI);

		assertThrows(IllegalArgumentException.class, () -> GeometrySimplify.calculate(source, 0));
	}

	@Test
	public void measuredPolygonRetainsOrdinatesWhenACollinearVertexIsRemoved() {
		GeometryWrapper source = GeometryWrapper.extract(
				"POLYGON ZM((0 0 1 10,1 0.1 2 20,2 0 3 30,2 2 4 40,0 2 5 50,0 0 1 10))",
				WKTDatatype.URI);

		GeometryWrapper result = GeometrySimplify.calculate(source, 0.2);
		Polygon polygon = (Polygon) result.getParsingGeometry();
		var coordinates = polygon.getExteriorRing().getCoordinateSequence();

		assertEquals(4, result.getCoordinateDimension());
		assertEquals(5, coordinates.size());
		double[] expectedZ = {1, 3, 4, 5, 1};
		for (int i = 0; i < coordinates.size(); i++) {
			assertEquals(expectedZ[i], coordinates.getZ(i), 0.0);
			assertEquals(expectedZ[i] * 10, coordinates.getM(i), 0.0);
		}
	}

	@Test
	public void planarPolygonRepairRemainsSupported() {
		GeometryWrapper source = GeometryWrapper.extract(
				"POLYGON((0 0,2 2,0 2,2 0,0 0))", WKTDatatype.URI);

		GeometryWrapper result = GeometrySimplify.calculate(source, 0);

		assertTrue(result.getParsingGeometry().isValid());
		assertEquals(1.0, result.getParsingGeometry().getArea(), 0.0);
		assertEquals(2, result.getCoordinateDimension());
	}

	@Test
	public void coincidentSurvivingVerticesWithAmbiguousMeasuresAreRejected() {
		GeometryWrapper source = GeometryWrapper.extract(
				"LINESTRING M(0 0 1,1 0.1 2,0 0 3)", WKTDatatype.URI);

		assertThrows(IllegalArgumentException.class, () -> GeometrySimplify.calculate(source, 0));
	}

	@Test
	public void negativeToleranceIsRejected() {
		GeometryWrapper source = GeometryWrapper.extract("LINESTRING(0 0,1 0.1,2 0)", WKTDatatype.URI);

		assertThrows(IllegalArgumentException.class,
				() -> GeometrySimplify.calculate(source, -0.1));
	}
}
