package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;

/**
 * Calculates the structural topological dimension of a geometry.
 */
public final class TopologicalDimension {
	private TopologicalDimension() {
	}

	public static int calculate(GeometryWrapper geometry) {
		return geometry.getXYGeometry().getDimension();
	}
}
