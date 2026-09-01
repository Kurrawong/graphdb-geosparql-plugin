package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.UnitsConversionException;
import org.apache.jena.geosparql.implementation.UnitsOfMeasure;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.apache.sis.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.cs.CoordinateSystem;

import javax.measure.IncommensurableException;
import javax.measure.Unit;

public final class GeometryArea {
	private GeometryArea() {
	}

	public static double calculateMetric(GeometryWrapper geometry) {
		return calculate(geometry, Unit_URI.METRE_URL);
	}

	public static double calculate(GeometryWrapper geometry, String targetUnitUri) {
		UnitsOfMeasure targetUnits = new UnitsOfMeasure(targetUnitUri);
		if (!targetUnits.isLinearUnits()) {
			throw new UnitsConversionException("Area requires linear target units.");
		}
		Geometry xyGeometry = geometry.getXYGeometry();
		if (!(xyGeometry instanceof Polygon || xyGeometry instanceof MultiPolygon)
				|| xyGeometry.isEmpty()) {
			return 0.0;
		}
		if (geometry.getSrsInfo().isGeographic()) {
			throw new UnitsConversionException("Area is not supported for geographic coordinate reference systems.");
		}
		UnitsOfMeasure sourceUnits = equivalentHorizontalAxisUnits(geometry);
		double conversionFactor = UnitsOfMeasure.conversion(
				1.0, sourceUnits, targetUnits);
		return xyGeometry.getArea() * (conversionFactor * conversionFactor);
	}

	private static UnitsOfMeasure equivalentHorizontalAxisUnits(GeometryWrapper geometry) {
		CoordinateReferenceSystem horizontalCrs = CRS.getHorizontalComponent(
				geometry.getSrsInfo().getCrs());
		if (horizontalCrs == null || horizontalCrs.getCoordinateSystem() == null
				|| horizontalCrs.getCoordinateSystem().getDimension() != 2) {
			throw new UnitsConversionException(
					"Area requires a two-dimensional horizontal source coordinate system.");
		}
		CoordinateSystem coordinateSystem = horizontalCrs.getCoordinateSystem();
		Unit<?> firstAxisUnit = coordinateSystem.getAxis(0).getUnit();
		Unit<?> secondAxisUnit = coordinateSystem.getAxis(1).getUnit();
		try {
			if (!firstAxisUnit.isCompatible(secondAxisUnit)
					|| !firstAxisUnit.getConverterToAny(secondAxisUnit).isIdentity()) {
				throw new UnitsConversionException(
						"Area requires equivalent linear units on both horizontal source axes.");
			}
		} catch (IncommensurableException e) {
			throw new UnitsConversionException(
					"Area requires equivalent linear units on both horizontal source axes.", e);
		}
		UnitsOfMeasure sourceUnits = new UnitsOfMeasure(horizontalCrs);
		if (!sourceUnits.isLinearUnits()) {
			throw new UnitsConversionException(
					"Area requires linear units on both horizontal source axes.");
		}
		return sourceUnits;
	}
}
