package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.GeometryDatatype;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.registry.SRSRegistry;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MetricWithinDistanceTest {
	private static final String EPSG_2227 = "http://www.opengis.net/def/crs/EPSG/0/2227";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";

	@BeforeClass
	public static void initializeJena() {
		SRSRegistry.setupDefaultSRS();
		GeometryDatatype.registerDatatypes();
	}

	@Test
	public void metricBoundaryIsInclusive() throws Exception {
		GeometryWrapper left = geometry("<" + EPSG_32634 + "> POINT(500000 4600000)");
		GeometryWrapper right = geometry("<" + EPSG_32634 + "> POINT(500003 4600004)");

		assertTrue(MetricWithinDistance.calculate(left, right, 5.0));
		assertFalse(MetricWithinDistance.calculate(left, right, 4.999));
	}

	@Test
	public void projectedNonMetreCrsUsesMetreThreshold() throws Exception {
		GeometryWrapper left = geometry("<" + EPSG_2227 + "> POINT(6300000 2000000)");
		GeometryWrapper right = geometry("<" + EPSG_2227 + "> POINT(6300003 2000004)");

		assertTrue(MetricWithinDistance.calculate(left, right, 2.0));
		assertFalse(MetricWithinDistance.calculate(left, right, 1.0));
	}

	private GeometryWrapper geometry(String wkt) {
		return GeometryWrapper.extract(wkt, WKTDatatype.URI);
	}
}
