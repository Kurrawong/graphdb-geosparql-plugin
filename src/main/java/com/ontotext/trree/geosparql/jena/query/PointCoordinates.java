package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Point;

/** Reads ordinates from a Point in its source coordinate order. */
public final class PointCoordinates {
	private PointCoordinates() {
	}

	public static double x(GeometryWrapper geometry) {
		return finite(sourceCoordinates(geometry).getX(0), "X");
	}

	public static double y(GeometryWrapper geometry) {
		return finite(sourceCoordinates(geometry).getY(0), "Y");
	}

	public static double z(GeometryWrapper geometry) {
		CoordinateSequence coordinates = sourceCoordinates(geometry);
		if (!coordinates.hasZ()) {
			throw new IllegalArgumentException("Point has no Z ordinate");
		}
		return finite(coordinates.getZ(0), "Z");
	}

	public static double m(GeometryWrapper geometry) {
		CoordinateSequence coordinates = sourceCoordinates(geometry);
		if (!coordinates.hasM()) {
			throw new IllegalArgumentException("Point has no M ordinate");
		}
		return finite(coordinates.getM(0), "M");
	}

	static CoordinateSequence sourceCoordinates(GeometryWrapper geometry) {
		if (!(geometry.getParsingGeometry() instanceof Point point)) {
			throw new IllegalArgumentException("Geometry must be a Point");
		}
		if (point.isEmpty()) {
			throw new IllegalArgumentException("Point must not be empty");
		}
		return point.getCoordinateSequence();
	}

	private static double finite(double value, String ordinate) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("Point " + ordinate + " ordinate must be finite");
		}
		return value;
	}
}
