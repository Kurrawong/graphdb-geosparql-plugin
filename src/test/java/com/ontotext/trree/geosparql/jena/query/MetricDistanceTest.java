package com.ontotext.trree.geosparql.jena.query;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MetricDistanceTest {
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";

	@BeforeClass
	public static void initializeJena() {
		JenaGeometryAdapter.initialize();
	}

	@Test
	public void projectedDistanceIsReturnedInMetres() throws Exception {
		GeometryWrapper left = geometry("<" + EPSG_32634 + "> POINT(500000 4600000)");
		GeometryWrapper right = geometry("<" + EPSG_32634 + "> POINT(500003 4600004)");

		assertEquals(5.0, MetricDistance.calculate(left, right), 0.0);
	}

	private GeometryWrapper geometry(String wkt) {
		return SourceGeometryLiteral.fromWkt(wkt).asGeometryWrapper();
	}
}
