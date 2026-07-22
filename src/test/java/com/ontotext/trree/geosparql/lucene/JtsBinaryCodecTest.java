package com.ontotext.trree.geosparql.lucene;

import org.junit.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.context.jts.JtsSpatialContextFactory;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.jts.JtsGeometry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JtsBinaryCodecTest {
	@Test
	public void spatial4jBinaryLayoutRoundTripsWithCurrentInStreamContract() throws Exception {
		JtsSpatialContextFactory factory = new JtsSpatialContextFactory();
		factory.geo = true;
		factory.binaryCodecClass = JtsBinaryCodec.class;
		JtsSpatialContext context = factory.newSpatialContext();
		Geometry geometry = new WKTReader().read("POLYGON((0 0,0 2,2 2,2 0,0 0))");
		JtsGeometry shape = new JtsGeometry(geometry, context, true, true);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		context.getBinaryCodec().writeShape(new DataOutputStream(bytes), shape);
		Shape decoded = context.getBinaryCodec().readShape(
				new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

		assertTrue(context.getBinaryCodec() instanceof JtsBinaryCodec);
		assertEquals(geometry, context.getShapeFactory().getGeometryFrom(decoded));
	}
}
