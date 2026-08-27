package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.junit.Test;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeometryBoundingCircleTest {
	@Test
	public void positiveRadiusProducesTheDocumentedCircumscribedPolygon() {
		GeometryWrapper source = wrapper("MULTIPOINT((0 0),(2 0))");

		GeometryWrapper firstResult = GeometryBoundingCircle.calculate(source);
		GeometryWrapper secondResult = GeometryBoundingCircle.calculate(source);
		Polygon polygon = (Polygon) firstResult.getXYGeometry();
		Coordinate[] vertices = polygon.getExteriorRing().getCoordinates();
		Point centre = polygon.getFactory().createPoint(new Coordinate(1, 0));
		double minimumVertexRadius = Math.nextUp(1.0 / Math.cos(Math.PI / 32.0));

		assertEquals(32, polygon.getExteriorRing().getNumPoints() - 1);
		assertEquals(minimumVertexRadius, centre.getCoordinate().distance(vertices[0]), 0.0);
		assertTrue(vertices[0].x > centre.getX());
		assertEquals(0.0, vertices[0].y, 0.0);
		assertTrue(Orientation.isCCW(vertices));
		assertEquals(vertices[0], vertices[vertices.length - 1]);
		assertTrue(polygon.covers(source.getXYGeometry()));
		assertTrue(polygon.getBoundary().distance(centre) >= 1.0);
		assertTrue(firstResult.getXYGeometry().equalsExact(secondResult.getXYGeometry()));
	}

	@Test
	public void emptyAndSingleCoordinateInputsReturnXyDegenerateResults() {
		GeometryWrapper empty = GeometryBoundingCircle.calculate(wrapper("POINT Z EMPTY"));
		GeometryWrapper point = GeometryBoundingCircle.calculate(wrapper(
				"MULTIPOINT Z((1 2 3),(1 2 9))"));

		assertEquals("Polygon", empty.getGeometryType());
		assertTrue(empty.isEmpty());
		assertEquals("Point", point.getGeometryType());
		assertEquals(1.0, point.getXYGeometry().getCoordinate().x, 0.0);
		assertEquals(2.0, point.getXYGeometry().getCoordinate().y, 0.0);
		for (GeometryWrapper result : new GeometryWrapper[]{empty, point}) {
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
