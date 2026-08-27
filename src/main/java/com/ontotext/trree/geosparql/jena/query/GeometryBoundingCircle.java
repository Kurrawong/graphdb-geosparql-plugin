package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;
import org.apache.jena.geosparql.implementation.jts.CustomCoordinateSequence;
import org.apache.jena.geosparql.implementation.jts.CustomGeometryFactory;
import org.locationtech.jts.algorithm.MinimumBoundingCircle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * Calculates a conservative polygonal representation of the planar minimum bounding circle.
 * Positive-radius results are deterministic 32-sided circumscribed polygons rather than analytic circles.
 */
public final class GeometryBoundingCircle {
	private static final int SEGMENT_COUNT = 32;
	private static final double ANGLE_INCREMENT = 2.0 * Math.PI / SEGMENT_COUNT;

	private GeometryBoundingCircle() {
	}

	public static GeometryWrapper calculate(GeometryWrapper geometry) {
		Geometry source = geometry.getXYGeometry();
		GeometryFactory factory = CustomGeometryFactory.theInstance();
		Geometry result;
		if (source.isEmpty()) {
			result = factory.createPolygon();
		} else if (hasOneUniqueCoordinate(source)) {
			Coordinate coordinate = source.getCoordinate();
			result = factory.createPoint(CustomCoordinateSequence.createPoint(
					coordinate.x, coordinate.y));
		} else {
			MinimumBoundingCircle calculation = new MinimumBoundingCircle(source);
			Coordinate centre = calculation.getCentre();
			double radius = calculation.getRadius();
			result = circumscribedPolygon(factory, centre, radius);
		}
		return new GeometryWrapper(result, geometry.getSrsURI(), geometry.getGeometryDatatypeURI(),
				new DimensionInfo(2, 2, result.getDimension()));
	}

	private static boolean hasOneUniqueCoordinate(Geometry geometry) {
		Coordinate[] coordinates = geometry.getCoordinates();
		Coordinate first = coordinates[0];
		for (int index = 1; index < coordinates.length; index++) {
			if (!first.equals2D(coordinates[index])) {
				return false;
			}
		}
		return true;
	}

	private static Geometry circumscribedPolygon(
			GeometryFactory factory, Coordinate centre, double radius) {
		double vertexRadius = Math.nextUp(radius / Math.cos(Math.PI / SEGMENT_COUNT));
		CustomCoordinateSequence vertices = new CustomCoordinateSequence(
				SEGMENT_COUNT + 1, CoordinateSequenceDimensions.XY);
		for (int index = 0; index < SEGMENT_COUNT; index++) {
			double angle = index * ANGLE_INCREMENT;
			vertices.setOrdinate(index, 0, centre.x + vertexRadius * Math.cos(angle));
			vertices.setOrdinate(index, 1, centre.y + vertexRadius * Math.sin(angle));
		}
		vertices.setOrdinate(SEGMENT_COUNT, 0, vertices.getX(0));
		vertices.setOrdinate(SEGMENT_COUNT, 1, vertices.getY(0));
		return factory.createPolygon(vertices);
	}
}
