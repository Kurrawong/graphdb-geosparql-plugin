package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PointCoordinatesTest {
	private static final String EPSG_4326 = "<http://www.opengis.net/def/crs/EPSG/0/4326> ";

	@Test
	public void xyFollowSourceOrderForAuthorityAxisCrs() {
		GeometryWrapper point = wkt(EPSG_4326 + "POINT(10 100)");

		assertEquals(10.0, PointCoordinates.x(point), 0.0);
		assertEquals(100.0, PointCoordinates.y(point), 0.0);
	}

	@Test
	public void zAndMUseTheirDeclaredCoordinateSlots() {
		GeometryWrapper measured = wkt("POINT M(1 2 7)");
		GeometryWrapper fourDimensional = wkt(EPSG_4326 + "POINT ZM(10 100 3 7)");

		assertEquals(7.0, PointCoordinates.m(measured), 0.0);
		assertEquals(3.0, PointCoordinates.z(fourDimensional), 0.0);
		assertEquals(7.0, PointCoordinates.m(fourDimensional), 0.0);
		assertThrows(IllegalArgumentException.class, () -> PointCoordinates.z(measured));
	}

	@Test
	public void missingPointOrOrdinateIsAnError() {
		assertThrows(IllegalArgumentException.class,
				() -> PointCoordinates.x(wkt("LINESTRING(1 2,3 4)")));
		assertThrows(IllegalArgumentException.class,
				() -> PointCoordinates.y(wkt("POINT EMPTY")));
		assertThrows(IllegalArgumentException.class,
				() -> PointCoordinates.m(wkt("POINT Z(1 2 3)")));
	}

	private GeometryWrapper wkt(String lexicalForm) {
		return GeometryWrapper.extract(lexicalForm, WKTDatatype.URI);
	}
}
