package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;

public final class MetricWithinDistance {
	private MetricWithinDistance() {
	}

	public static boolean calculate(GeometryWrapper left, GeometryWrapper right, double distance)
			throws Exception {
		return WithinDistance.calculate(left, right, distance, Unit_URI.METRE_URL);
	}
}
