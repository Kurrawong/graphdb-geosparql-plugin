package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;
import org.apache.jena.geosparql.implementation.jts.CustomCoordinateSequence;
import org.apache.jena.geosparql.implementation.jts.CustomGeometryFactory;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GeometryCoordinateExtremaTest {
	@Test
	public void zExtremaIgnoreNonFiniteSentinels() {
		CustomCoordinateSequence coordinates = new CustomCoordinateSequence(
				CoordinateSequenceDimensions.XYZ,
				"0 0 NaN,1 1 -4,2 2 Infinity,3 3 9");
		GeometryWrapper geometry = wrapper(
				CustomGeometryFactory.theInstance().createLineString(coordinates),
				CoordinateSequenceDimensions.XYZ);

		assertEquals(-4.0, GeometryCoordinateExtrema.minZ(geometry), 0.0);
		assertEquals(9.0, GeometryCoordinateExtrema.maxZ(geometry), 0.0);
	}

	@Test
	public void zExtremaFailWhenNoFiniteZRemains() {
		CustomCoordinateSequence coordinates = new CustomCoordinateSequence(
				CoordinateSequenceDimensions.XYZ,
				"0 0 NaN,1 1 Infinity");
		GeometryWrapper geometry = wrapper(
				CustomGeometryFactory.theInstance().createLineString(coordinates),
				CoordinateSequenceDimensions.XYZ);

		assertThrows(IllegalArgumentException.class,
				() -> GeometryCoordinateExtrema.minZ(geometry));
		assertThrows(IllegalArgumentException.class,
				() -> GeometryCoordinateExtrema.maxZ(geometry));
	}

	@Test
	public void zExtremaRequireZBearingDimensionInfo() {
		CoordinateSequence coordinates = new CoordinateArraySequence(
				new Coordinate[]{new Coordinate(1, 2, 99)}, 3, 1);
		GeometryWrapper geometry = wrapper(
				CustomGeometryFactory.theInstance().createPoint(coordinates),
				CoordinateSequenceDimensions.XYM);

		assertThrows(IllegalArgumentException.class,
				() -> GeometryCoordinateExtrema.minZ(geometry));
		assertThrows(IllegalArgumentException.class,
				() -> GeometryCoordinateExtrema.maxZ(geometry));
	}

	private GeometryWrapper wrapper(Geometry geometry, CoordinateSequenceDimensions dimensions) {
		return new GeometryWrapper(geometry, SRS_URI.DEFAULT_WKT_CRS84, WKTDatatype.URI,
				new DimensionInfo(dimensions, geometry.getDimension()));
	}
}
