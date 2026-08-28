package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;

public final class MetricDistance {
	private MetricDistance() {
	}

	public static double calculate(GeometryWrapper left, GeometryWrapper right)
			throws Exception {
		return left.distance(right, Unit_URI.METRE_URL);
	}
}
