package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.UnitsConversionException;
import org.apache.jena.geosparql.implementation.UnitsOfMeasure;

public final class WithinDistance {
	private WithinDistance() {
	}

	public static boolean calculate(GeometryWrapper left, GeometryWrapper right, double distance, String unitUri)
			throws Exception {
		UnitsOfMeasure requestedUnits = new UnitsOfMeasure(unitUri);
		if (!requestedUnits.isLinearUnits()) {
			throw new UnitsConversionException("Distance thresholds require linear units.");
		}
		UnitsOfMeasure calculationUnits = left.getSrsInfo().isGeographic()
				? UnitsOfMeasure.METRE_UNITS : left.getUnitsOfMeasure();
		double threshold = requestedUnits.getUnit().getConverterTo(calculationUnits.getUnit()).convert(distance);
		double measuredDistance = left.distance(right, calculationUnits.getUnitURI());
		return measuredDistance <= threshold;
	}
}
