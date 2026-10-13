package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.UnitsOfMeasure;
import org.apache.jena.geosparql.implementation.UnitsConversionException;
import org.apache.jena.geosparql.implementation.great_circle.CoordinatePair;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;

public final class GeometryDistance {
	private GeometryDistance() {
	}

	public static double calculateMetric(GeometryWrapper left, GeometryWrapper right) throws Exception {
		return linearDistance(left, right, Unit_URI.METRE_URL);
	}

	public static double calculate(GeometryWrapper left, GeometryWrapper right, String unitUri) throws Exception {
		return switch (unitUri) {
			case Unit_URI.DEGREE_URL, Unit_URI.RADIAN_URL -> {
				double radians = calculateMetric(left, right) / UnitsOfMeasure.EARTH_MEAN_RADIUS;
				yield Unit_URI.DEGREE_URL.equals(unitUri) ? Math.toDegrees(radians) : radians;
			}
			case "http://www.opengis.net/def/uom/OGC/1.0/GridSpacing",
					"http://www.opengis.net/def/uom/OGC/1.0/unity" ->
					left.getXYGeometry().distance(left.checkTransformSRS(right).getXYGeometry());
			default -> linearDistance(left, right, unitUri);
		};
	}

	private static double linearDistance(GeometryWrapper left, GeometryWrapper right, String unitUri) throws Exception {
		if (!left.getSrsInfo().isGeographic()) {
			return left.distance(right, unitUri);
		}
		UnitsOfMeasure targetUnits = new UnitsOfMeasure(unitUri);
		if (!targetUnits.isLinearUnits()) {
			throw new UnitsConversionException("Great-circle distance requires linear target units.");
		}
		GeometryWrapper transformedRight = left.checkTransformSRS(right);
		CoordinatePair pair = CoordinatePair.findNearestPair(left, transformedRight);
		if (pair.isEqual()) {
			return 0.0;
		}
		double metres = new GeographicDistance(left).calculate(
				pair.getCoord1().getX(), pair.getCoord1().getY(),
				pair.getCoord2().getX(), pair.getCoord2().getY());
		return UnitsOfMeasure.conversion(metres, UnitsOfMeasure.METRE_UNITS, targetUnits);
	}
}
