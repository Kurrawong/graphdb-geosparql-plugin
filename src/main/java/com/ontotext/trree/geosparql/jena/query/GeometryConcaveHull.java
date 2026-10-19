package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.GeometryWrapperFactory;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;
import org.apache.jena.geosparql.implementation.jts.CustomCoordinateSequence;
import org.locationtech.jts.algorithm.hull.ConcaveHull;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.GeometryTransformer;

/**
 * Calculates the planar concave hull from the complete input vertex set with holes disabled. The maximum
 * edge-length ratio ranges from zero, the tightest hull, to one, the convex hull.
 */
public final class GeometryConcaveHull {
	/** The JTS 1.20 default criterion: maximum concaveness. */
	private static final double DEFAULT_MAXIMUM_EDGE_LENGTH_RATIO = 0.0;

	private GeometryConcaveHull() {
	}

	public static GeometryWrapper calculate(GeometryWrapper geometry) {
		return calculate(geometry, DEFAULT_MAXIMUM_EDGE_LENGTH_RATIO);
	}

	public static GeometryWrapper calculate(GeometryWrapper geometry, double maximumEdgeLengthRatio) {
		ConcaveHull calculation = new ConcaveHull(geometry.getXYGeometry());
		calculation.setMaximumEdgeLengthRatio(maximumEdgeLengthRatio);
		calculation.setHolesAllowed(false);
		Geometry result = xyGeometry(calculation.getHull());
		if (result.isEmpty()) {
			return new GeometryWrapper(result, geometry.getSrsURI(), geometry.getGeometryDatatypeURI(),
					new DimensionInfo(2, 2, result.getDimension()));
		}
		return GeometryWrapperFactory.createGeometry(
				result, geometry.getSrsURI(), geometry.getGeometryDatatypeURI());
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
