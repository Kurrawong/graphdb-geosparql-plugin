package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.UnitsOfMeasure;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;

public final class GeometryDistance {
	private GeometryDistance() {
	}

	public static double calculate(GeometryWrapper left, GeometryWrapper right, String unitUri) throws Exception {
		return switch (unitUri) {
			case Unit_URI.DEGREE_URL, Unit_URI.RADIAN_URL -> {
				double radians = left.distance(right, Unit_URI.METRE_URL) / UnitsOfMeasure.EARTH_MEAN_RADIUS;
				yield Unit_URI.DEGREE_URL.equals(unitUri) ? Math.toDegrees(radians) : radians;
			}
			case "http://www.opengis.net/def/uom/OGC/1.0/GridSpacing",
					"http://www.opengis.net/def/uom/OGC/1.0/unity" ->
					left.getXYGeometry().distance(left.checkTransformSRS(right).getXYGeometry());
			default -> left.distance(right, unitUri);
		};
	}
}
