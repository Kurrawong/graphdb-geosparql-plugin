package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeometryCentroidTest {
	@Test
	public void mixedCollectionCentroidUsesTheHighestTopologicalDimension() {
		GeometryWrapper centroid = GeometryCentroid.calculate(wrapper(
				"GEOMETRYCOLLECTION(POINT(80 80),LINESTRING(0 0,10 0))"));

		assertEquals("Point", centroid.getGeometryType());
		assertEquals(5.0, centroid.getXYGeometry().getCoordinate().x, 0.0);
		assertEquals(0.0, centroid.getXYGeometry().getCoordinate().y, 0.0);
	}

	@Test
	public void centroidIsAnXyPointForEmptyAndZInputs() {
		GeometryWrapper empty = GeometryCentroid.calculate(wrapper("GEOMETRYCOLLECTION EMPTY"));
		GeometryWrapper zLine = GeometryCentroid.calculate(wrapper("LINESTRING Z(0 0 9,2 0 7)"));

		assertEquals("Point", empty.getGeometryType());
		assertTrue(empty.isEmpty());
		assertEquals(2, empty.getCoordinateDimension());
		assertEquals(2, zLine.getCoordinateDimension());
		assertEquals(2, zLine.getSpatialDimension());
	}

	private GeometryWrapper wrapper(String wkt) {
		return GeometryWrapper.extract(wkt, WKTDatatype.URI);
	}
}
