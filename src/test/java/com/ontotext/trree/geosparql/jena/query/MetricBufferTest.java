package com.ontotext.trree.geosparql.jena.query;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MetricBufferTest {
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";

	@BeforeClass
	public static void initializeJena() {
		JenaGeometryAdapter.initialize();
	}

	@Test
	public void projectedRadiusIsInterpretedInMetres() throws Exception {
		GeometryWrapper point = geometry("<" + EPSG_32634 + "> POINT(500000 4600000)");

		GeometryWrapper result = MetricBuffer.calculate(point, 1.0);

		assertEquals("Polygon", result.getGeometryType());
		assertEquals(EPSG_32634, result.getSrsURI());
		assertFalse(result.isEmpty());
		assertEquals(499999.0, result.getXYGeometry().getEnvelopeInternal().getMinX(), 0.0);
		assertEquals(500001.0, result.getXYGeometry().getEnvelopeInternal().getMaxX(), 0.0);
	}

	private GeometryWrapper geometry(String wkt) {
		return SourceGeometryLiteral.fromWkt(wkt).asGeometryWrapper();
	}
}
