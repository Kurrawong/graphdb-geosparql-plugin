package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.UnitsConversionException;
import org.apache.jena.geosparql.implementation.UnitsOfMeasure;
import org.apache.sis.measure.Units;
import org.opengis.referencing.cs.CoordinateSystem;

import javax.measure.IncommensurableException;
import javax.measure.UnitConverter;

/** Calculates spherical distance in metres from source-CRS XY coordinates. */
final class GeographicDistance {
	private final UnitConverter xToDegrees;
	private final UnitConverter yToDegrees;

	GeographicDistance(GeometryWrapper geometry) {
		CoordinateSystem sourceAxes = geometry.getSrsInfo().getCrs().getCoordinateSystem();
		int xAxis = geometry.getSrsInfo().isAxisXY() ? 0 : 1;
		try {
			xToDegrees = sourceAxes.getAxis(xAxis).getUnit().getConverterToAny(Units.DEGREE);
			yToDegrees = sourceAxes.getAxis(1 - xAxis).getUnit().getConverterToAny(Units.DEGREE);
		} catch (IncommensurableException e) {
			throw new UnitsConversionException("Geographic measurements require angular source axis units.", e);
		}
	}

	double calculate(double x1, double y1, double x2, double y2) {
		return greatCircleDistance(yToDegrees.convert(y1), xToDegrees.convert(x1),
				yToDegrees.convert(y2), xToDegrees.convert(x2));
	}

	private static double greatCircleDistance(double latitude1, double longitude1,
			double latitude2, double longitude2) {
		double latitude1Radians = Math.toRadians(latitude1);
		double latitude2Radians = Math.toRadians(latitude2);
		double sinLatitude = Math.sin(Math.toRadians(latitude2 - latitude1) / 2.0);
		double sinLongitude = Math.sin(Math.toRadians(longitude2 - longitude1) / 2.0);
		double haversine = sinLatitude * sinLatitude
				+ Math.cos(latitude1Radians) * Math.cos(latitude2Radians) * (sinLongitude * sinLongitude);
		// Round-off at antipodal endpoints can put the haversine just outside its mathematical range.
		haversine = Math.max(0.0, Math.min(1.0, haversine));
		return 2.0 * UnitsOfMeasure.EARTH_MEAN_RADIUS
				* Math.atan2(Math.sqrt(haversine), Math.sqrt(1.0 - haversine));
	}
}
