package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import static org.junit.Assert.assertEquals;

public class GeometryCountTest {
	@Test
	public void countsJtsDirectStructuralMembers() throws ParseException {
		assertEquals(1, GeometryCount.calculate(wrapper("POINT EMPTY")));
		assertEquals(0, GeometryCount.calculate(wrapper("MULTIPOINT EMPTY")));
		assertEquals(2, GeometryCount.calculate(wrapper(
				"GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(3 4,5 6))")));
	}

	private GeometryWrapper wrapper(String wkt) throws ParseException {
		Geometry geometry = new WKTReader().read(wkt);
		return new GeometryWrapper(geometry, SRS_URI.DEFAULT_WKT_CRS84, WKTDatatype.URI,
				new DimensionInfo(2, 2, geometry.getDimension()));
	}
}
