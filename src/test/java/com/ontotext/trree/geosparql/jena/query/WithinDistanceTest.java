package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.GeometryDatatype;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.registry.SRSRegistry;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WithinDistanceTest {
	private static final String EPSG_2227 = "http://www.opengis.net/def/crs/EPSG/0/2227";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String KILOMETRE = "http://www.opengis.net/def/uom/OGC/1.0/kilometre";
	private static final String METRE = "http://www.opengis.net/def/uom/OGC/1.0/metre";

	@BeforeClass
	public static void initializeJena() {
		SRSRegistry.setupDefaultSRS();
		GeometryDatatype.registerDatatypes();
	}

	@Test
	public void requestedUnitControlsTheInclusiveBoundary() throws Exception {
		GeometryWrapper left = geometry("<" + EPSG_32634 + "> POINT(500000 4600000)");
		GeometryWrapper right = geometry("<" + EPSG_32634 + "> POINT(500003 4600004)");

		assertTrue(WithinDistance.calculate(left, right, 0.005, KILOMETRE));
		assertFalse(WithinDistance.calculate(left, right, 0.004, KILOMETRE));
		assertFalse(WithinDistance.calculate(left, right, -1.0, KILOMETRE));
	}

	@Test
	public void projectedNonMetreCrsUsesTheRequestedLinearUnit() throws Exception {
		GeometryWrapper left = geometry("<" + EPSG_2227 + "> POINT(6300000 2000000)");
		GeometryWrapper right = geometry("<" + EPSG_2227 + "> POINT(6300003 2000004)");

		assertTrue(WithinDistance.calculate(left, right, 2.0, METRE));
		assertFalse(WithinDistance.calculate(left, right, 1.0, METRE));
	}

	private GeometryWrapper geometry(String wkt) {
		return GeometryWrapper.extract(wkt, WKTDatatype.URI);
	}
}
