package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.GeometryWrapperFactory;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.junit.Test;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import static org.junit.Assert.assertEquals;

public class TopologicalDimensionTest {
	@Test
	public void calculatesTopologicalDimensionFromGeometryWrapper() throws ParseException {
		GeometryWrapper geometry = GeometryWrapperFactory.createGeometry(
				new WKTReader().read("GEOMETRYCOLLECTION(POINT(1 1),LINESTRING(0 0,1 1))"),
				SRS_URI.DEFAULT_WKT_CRS84, WKTDatatype.URI);

		assertEquals(1, TopologicalDimension.calculate(geometry));
	}
}
