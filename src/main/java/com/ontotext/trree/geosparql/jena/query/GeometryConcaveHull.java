package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;
import org.apache.jena.geosparql.implementation.jts.CustomCoordinateSequence;
import org.locationtech.jts.algorithm.hull.ConcaveHull;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.GeometryTransformer;

/**
 * Calculates the planar concave hull from the complete input vertex set using maximum-edge-length ratio zero
 * with holes disabled.
 */
public final class GeometryConcaveHull {
	private GeometryConcaveHull() {
	}

	public static GeometryWrapper calculate(GeometryWrapper geometry) {
		ConcaveHull calculation = new ConcaveHull(geometry.getXYGeometry());
		calculation.setMaximumEdgeLengthRatio(0.0);
		calculation.setHolesAllowed(false);
		Geometry result = xyGeometry(calculation.getHull());
		return new GeometryWrapper(result, geometry.getSrsURI(), geometry.getGeometryDatatypeURI(),
				new DimensionInfo(2, 2, result.getDimension()));
	}

	private static Geometry xyGeometry(Geometry geometry) {
		return new GeometryTransformer() {
			@Override
			protected CoordinateSequence transformCoordinates(
					CoordinateSequence coordinates, Geometry parent) {
				CoordinateSequence result = new CustomCoordinateSequence(
						coordinates.size(), CoordinateSequenceDimensions.XY);
				for (int index = 0; index < coordinates.size(); index++) {
					result.setOrdinate(index, 0, coordinates.getX(index));
					result.setOrdinate(index, 1, coordinates.getY(index));
				}
				return result;
			}
		}.transform(geometry);
	}
}
