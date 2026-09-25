package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeometryConcaveHullTest {
	@Test
	public void concaveHullUsesTheMostConcaveConnectedHoleFreeResult() {
		GeometryWrapper source = wrapper(
				"MULTIPOINT((30 90),(10 70),(30 70),(50 70),(70 70),(90 70),"
						+ "(20 40),(40 40),(60 40),(80 40),(0 0),(100 0))");

		GeometryWrapper result = GeometryConcaveHull.calculate(source);
		Geometry hull = result.getXYGeometry();

		assertEquals("Polygon", hull.getGeometryType());
		assertTrue(hull.covers(source.getXYGeometry()));
		assertEquals(3000.0, hull.getArea(), 0.0);
		assertTrue(hull.getArea() < source.getXYGeometry().convexHull().getArea());
		assertEquals(0, ((Polygon) hull).getNumInteriorRing());
	}

	@Test
	public void concaveHullReturnsPrimitiveSpecificXyResults() {
		GeometryWrapper empty = GeometryConcaveHull.calculate(wrapper("POINT Z EMPTY"));
		GeometryWrapper point = GeometryConcaveHull.calculate(wrapper("POINT Z(1 2 3)"));
		GeometryWrapper line = GeometryConcaveHull.calculate(wrapper(
				"MULTIPOINT Z((0 0 5),(1 1 6),(2 2 7))"));

		assertEquals("Polygon", empty.getGeometryType());
		assertTrue(empty.isEmpty());
		assertEquals("Point", point.getGeometryType());
		assertEquals("LineString", line.getGeometryType());
		for (GeometryWrapper result : new GeometryWrapper[]{empty, point, line}) {
			assertEquals(2, result.getCoordinateDimension());
			assertEquals(2, result.getSpatialDimension());
			for (Coordinate coordinate : result.getXYGeometry().getCoordinates()) {
				assertTrue(Double.isNaN(coordinate.getZ()));
				assertTrue(Double.isNaN(coordinate.getM()));
			}
		}
	}

	private GeometryWrapper wrapper(String wkt) {
		return GeometryWrapper.extract(wkt, WKTDatatype.URI);
	}
}
