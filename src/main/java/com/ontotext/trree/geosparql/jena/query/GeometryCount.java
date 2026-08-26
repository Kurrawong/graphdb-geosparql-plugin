package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;

/**
 * Counts the direct structural members of a geometry.
 */
public final class GeometryCount {
	private GeometryCount() {
	}

	public static int calculate(GeometryWrapper geometry) {
		return geometry.getXYGeometry().getNumGeometries();
	}
}
