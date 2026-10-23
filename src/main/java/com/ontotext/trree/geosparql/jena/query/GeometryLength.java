package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.UnitsOfMeasure;
import org.apache.jena.geosparql.implementation.UnitsConversionException;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;

public final class GeometryLength {
	private GeometryLength() {
	}

	public static double calculateMetric(GeometryWrapper geometry) {
		return calculate(geometry, Unit_URI.METRE_URL);
	}

	public static double calculate(GeometryWrapper geometry, String targetUnitUri) {
		UnitsOfMeasure targetUnits = new UnitsOfMeasure(targetUnitUri);
		if (!targetUnits.isLinearUnits()) {
			throw new UnitsConversionException("Linear measurement requires linear target units.");
		}
		if (geometry.getSrsInfo().isGeographic()) {
			return UnitsOfMeasure.conversion(greatCircleLength(geometry),
					UnitsOfMeasure.METRE_UNITS, targetUnits);
		}
		double sourceLength = geometry.getXYGeometry().getLength();
		return UnitsOfMeasure.conversion(sourceLength,
				geometry.getUnitsOfMeasure(), targetUnits);
	}

	private static double greatCircleLength(GeometryWrapper geometry) {
		GreatCircleLengthFilter filter = new GreatCircleLengthFilter(new GeographicDistance(geometry));
		geometry.getXYGeometry().apply(filter);
		return filter.length;
	}

	private static final class GreatCircleLengthFilter implements CoordinateSequenceFilter {
		private final GeographicDistance distance;
		private double length;

		private GreatCircleLengthFilter(GeographicDistance distance) {
			this.distance = distance;
		}

		@Override
		public void filter(CoordinateSequence sequence, int index) {
			if (index > 0) {
				length += distance.calculate(
						sequence.getX(index - 1), sequence.getY(index - 1),
						sequence.getX(index), sequence.getY(index));
			}
		}

		@Override
		public boolean isDone() {
			return false;
		}

		@Override
		public boolean isGeometryChanged() {
			return false;
		}
	}
}
