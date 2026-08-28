package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;

public final class MetricBuffer {
	private MetricBuffer() {
	}

	public static GeometryWrapper calculate(GeometryWrapper geometry, double radius)
			throws Exception {
		return geometry.buffer(radius, Unit_URI.METRE_URL);
	}
}
