package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.GeometryWrapperFactory;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

/** Applies Douglas-Peucker simplification in the source CRS coordinate units. */
public final class GeometrySimplify {
	private GeometrySimplify() {
	}

	public static GeometryWrapper calculate(GeometryWrapper geometry, double tolerance) {
		if (!Double.isFinite(tolerance) || tolerance < 0) {
			throw new IllegalArgumentException("Simplification tolerance must be finite and nonnegative");
		}
		Geometry simplified = DouglasPeuckerSimplifier.simplify(geometry.getXYGeometry(), tolerance);
		if (simplified.isEmpty()) {
			DimensionInfo dimensions = new DimensionInfo(
					geometry.getDimensionInfo().getDimensions(), simplified.getDimension());
			return new GeometryWrapper(simplified, geometry.getSrsURI(),
					geometry.getGeometryDatatypeURI(), dimensions);
		}
		return GeometryWrapperFactory.createGeometry(simplified,
				geometry.getSrsURI(), geometry.getGeometryDatatypeURI());
	}
}
