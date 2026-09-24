package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;

public final class WithinDistance {
	private WithinDistance() {
	}

	public static boolean calculate(GeometryWrapper left, GeometryWrapper right, double distance, String unitUri)
			throws Exception {
		return left.distance(right, unitUri) <= distance;
	}
}
