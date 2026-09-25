package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GeometrySimplifyTest {
	@Test
	public void emptyPointRemainsEmptyAtZeroTolerance() {
		GeometryWrapper source = GeometryWrapper.extract("POINT EMPTY", WKTDatatype.URI);

		GeometryWrapper result = GeometrySimplify.calculate(source, 0);

		assertTrue(result.isEmpty());
		assertEquals("Point", result.getGeometryType());
		assertEquals(source.getSrsURI(), result.getSrsURI());
	}

	@Test
	public void negativeToleranceIsRejected() {
		GeometryWrapper source = GeometryWrapper.extract("LINESTRING(0 0,1 0.1,2 0)", WKTDatatype.URI);

		assertThrows(IllegalArgumentException.class,
				() -> GeometrySimplify.calculate(source, -0.1));
	}
}
