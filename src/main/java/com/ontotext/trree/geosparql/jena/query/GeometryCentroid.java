package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.GeometryWrapperFactory;
import org.locationtech.jts.geom.Point;

/**
 * Calculates an XY centroid using JTS whole-geometry semantics.
 */
public final class GeometryCentroid {
	private GeometryCentroid() {
	}

	public static GeometryWrapper calculate(GeometryWrapper geometry) {
		Point centroid = geometry.getXYGeometry().getCentroid();
		if (centroid.isEmpty()) {
			return new GeometryWrapper(
					centroid, geometry.getSrsURI(), geometry.getGeometryDatatypeURI(), DimensionInfo.XY_POINT);
		}
		return GeometryWrapperFactory.createGeometry(
				centroid, geometry.getSrsURI(), geometry.getGeometryDatatypeURI());
	}
}
