package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.UnitsOfMeasure;
import org.apache.jena.geosparql.implementation.UnitsConversionException;
import org.apache.jena.geosparql.implementation.great_circle.GreatCircleDistance;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.apache.sis.measure.Units;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.opengis.referencing.cs.CoordinateSystem;

import javax.measure.IncommensurableException;
import javax.measure.UnitConverter;

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
		CoordinateSystem sourceAxes = geometry.getSrsInfo().getCrs().getCoordinateSystem();
		int xAxis = geometry.getSrsInfo().isAxisXY() ? 0 : 1;
		try {
			UnitConverter xToDegrees = sourceAxes.getAxis(xAxis).getUnit().getConverterToAny(Units.DEGREE);
			UnitConverter yToDegrees = sourceAxes.getAxis(1 - xAxis).getUnit().getConverterToAny(Units.DEGREE);
			GreatCircleLengthFilter filter = new GreatCircleLengthFilter(xToDegrees, yToDegrees);
			geometry.getXYGeometry().apply(filter);
			return filter.length;
		} catch (IncommensurableException e) {
			throw new UnitsConversionException("Geographic length requires angular source axis units.", e);
		}
	}

	private static final class GreatCircleLengthFilter implements CoordinateSequenceFilter {
		private final UnitConverter xToDegrees;
		private final UnitConverter yToDegrees;
		private double length;

		private GreatCircleLengthFilter(UnitConverter xToDegrees, UnitConverter yToDegrees) {
			this.xToDegrees = xToDegrees;
			this.yToDegrees = yToDegrees;
		}

		@Override
		public void filter(CoordinateSequence sequence, int index) {
			if (index > 0) {
				length += GreatCircleDistance.haversineFormula(
						yToDegrees.convert(sequence.getY(index - 1)), xToDegrees.convert(sequence.getX(index - 1)),
						yToDegrees.convert(sequence.getY(index)), xToDegrees.convert(sequence.getX(index)));
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
