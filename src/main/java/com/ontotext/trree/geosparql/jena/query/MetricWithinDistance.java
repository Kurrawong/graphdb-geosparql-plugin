package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;

public final class MetricWithinDistance {
	private MetricWithinDistance() {
	}

	public static boolean calculate(GeometryWrapper left, GeometryWrapper right, double distance)
			throws Exception {
		return left.distance(right) <= distance;
	}
}
