package com.ontotext.trree.geosparql.lucene;

import org.locationtech.jts.io.InStream;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.context.jts.JtsSpatialContextFactory;
import org.locationtech.spatial4j.exception.InvalidShapeException;
import org.locationtech.spatial4j.shape.Shape;

import java.io.DataInput;
import java.io.IOException;

/**
 * Spatial4j JTS codec adapted to the current {@code InStream} contract.
 *
 * <p>The surrounding Spatial4j binary shape format remains unchanged. Only the JTS stream adapter differs: JTS 1.20
 * returns the number of bytes read, while the Spatial4j 0.7 implementation was compiled against a void-returning
 * method.
 */
public final class JtsBinaryCodec extends org.locationtech.spatial4j.io.jts.JtsBinaryCodec {
	public JtsBinaryCodec(JtsSpatialContext context, JtsSpatialContextFactory factory) {
		super(context, factory);
	}

	@Override
	public Shape readJtsGeom(DataInput input) throws IOException {
		JtsSpatialContext context = (JtsSpatialContext) ctx;
		WKBReader reader = new WKBReader(context.getShapeFactory().getGeometryFactory());
		InStream stream = new InStream() {
			private boolean first = true;

			@Override
			public int read(byte[] buffer) throws IOException {
				if (first) {
					if (buffer.length != 1) {
						throw new IllegalStateException("Expected initial WKB byte-order read of one byte.");
					}
					// Spatial4j's outer type byte replaces the big-endian WKB byte-order byte emitted by its writer.
					buffer[0] = 0;
					first = false;
				} else {
					input.readFully(buffer);
				}
				return buffer.length;
			}
		};

		try {
			return context.getShapeFactory().makeShape(reader.read(stream), false, false);
		} catch (ParseException e) {
			throw new InvalidShapeException("Unable to read Spatial4j JTS geometry.", e);
		}
	}
}
