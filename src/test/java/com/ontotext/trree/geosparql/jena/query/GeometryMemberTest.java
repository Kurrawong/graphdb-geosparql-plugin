package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.GeometryWrapperFactory;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.junit.Test;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GeometryMemberTest {
	@Test
	public void selectsOneBasedDirectJtsMember() throws ParseException {
		GeometryWrapper source = wrapper(
				"GEOMETRYCOLLECTION(GEOMETRYCOLLECTION(POINT(1 2)),LINESTRING(3 4,5 6))");

		GeometryWrapper first = GeometryMember.calculate(source, 1);
		GeometryWrapper second = GeometryMember.calculate(source, 2);

		assertEquals("GeometryCollection", first.getGeometryType());
		assertEquals("LineString", second.getGeometryType());
		assertThrows(IllegalArgumentException.class, () -> GeometryMember.calculate(source, 0));
		assertThrows(IllegalArgumentException.class, () -> GeometryMember.calculate(source, 3));
	}

	private GeometryWrapper wrapper(String wkt) throws ParseException {
		return GeometryWrapperFactory.createGeometry(new WKTReader().read(wkt),
				SRS_URI.DEFAULT_WKT_CRS84, WKTDatatype.URI);
	}
}
