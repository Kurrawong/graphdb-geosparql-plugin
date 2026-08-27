package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;

import java.util.function.DoubleBinaryOperator;

/**
 * Finds extrema across a geometry's normalized coordinate sequences.
 */
public final class GeometryCoordinateExtrema {
	private GeometryCoordinateExtrema() {
	}

	public static double maxX(GeometryWrapper geometry) {
		return extreme(geometry, 0, Math::max);
	}

	public static double maxY(GeometryWrapper geometry) {
		return extreme(geometry, 1, Math::max);
	}

	public static double maxZ(GeometryWrapper geometry) {
		return zExtreme(geometry, Math::max);
	}

	public static double minX(GeometryWrapper geometry) {
		return extreme(geometry, 0, Math::min);
	}

	public static double minY(GeometryWrapper geometry) {
		return extreme(geometry, 1, Math::min);
	}

	public static double minZ(GeometryWrapper geometry) {
		return zExtreme(geometry, Math::min);
	}

	private static double extreme(GeometryWrapper geometry, int ordinate,
			DoubleBinaryOperator accumulator) {
		return extreme(geometry, ordinate, accumulator, false);
	}

	private static double zExtreme(GeometryWrapper geometry, DoubleBinaryOperator accumulator) {
		CoordinateSequenceDimensions dimensions = geometry.getDimensionInfo().getDimensions();
		if (dimensions != CoordinateSequenceDimensions.XYZ
				&& dimensions != CoordinateSequenceDimensions.XYZM) {
			throw new IllegalArgumentException("Geometry coordinate layout has no Z ordinate");
		}
		return extreme(geometry, 2, accumulator, true);
	}

	private static double extreme(GeometryWrapper geometry, int ordinate,
			DoubleBinaryOperator accumulator, boolean finiteOnly) {
		ExtremaFilter filter = new ExtremaFilter(ordinate, accumulator, finiteOnly);
		geometry.getXYGeometry().apply(filter);
		return filter.result();
	}

	private static final class ExtremaFilter implements CoordinateSequenceFilter {
		private final int ordinate;
		private final DoubleBinaryOperator accumulator;
		private final boolean finiteOnly;
		private double result;
		private boolean found;

		private ExtremaFilter(int ordinate, DoubleBinaryOperator accumulator, boolean finiteOnly) {
			this.ordinate = ordinate;
			this.accumulator = accumulator;
			this.finiteOnly = finiteOnly;
		}

		@Override
		public void filter(CoordinateSequence sequence, int index) {
			double value = sequence.getOrdinate(index, ordinate);
			if (finiteOnly && !Double.isFinite(value)) {
				return;
			}
			result = found ? accumulator.applyAsDouble(result, value) : value;
			found = true;
		}

		@Override
		public boolean isDone() {
			return false;
		}

		@Override
		public boolean isGeometryChanged() {
			return false;
		}

		private double result() {
			if (!found) {
				throw new IllegalArgumentException("Geometry has no eligible ordinate");
			}
			return result;
		}
	}
}
