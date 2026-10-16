package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.locationtech.jts.geom.CoordinateSequence;
import org.opengis.referencing.cs.AxisDirection;
import org.opengis.referencing.cs.CoordinateSystem;

/** Finds a Point ordinate by the direction of its source CRS axis. */
public final class DirectionalPointCoordinates {
	private DirectionalPointCoordinates() {
	}

	public static double easting(GeometryWrapper geometry) {
		return ordinate(geometry, AxisDirection.EAST);
	}

	public static double northing(GeometryWrapper geometry) {
		return ordinate(geometry, AxisDirection.NORTH);
	}

	private static double ordinate(GeometryWrapper geometry, AxisDirection direction) {
		CoordinateSequence coordinates = PointCoordinates.sourceCoordinates(geometry);
		CoordinateSystem coordinateSystem = geometry.getSrsInfo().getCrs().getCoordinateSystem();
		for (int axis = 0; axis < Math.min(2, coordinateSystem.getDimension()); axis++) {
			if (direction.equals(coordinateSystem.getAxis(axis).getDirection())) {
				double value = coordinates.getOrdinate(0, axis);
				if (!Double.isFinite(value)) {
					throw new IllegalArgumentException("Point coordinate must be finite");
				}
				return value;
			}
		}
		throw new IllegalArgumentException("Point CRS has no " + direction + " axis");
	}
}
