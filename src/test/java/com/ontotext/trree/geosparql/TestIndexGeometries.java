package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class TestIndexGeometries {
	private TestIndexGeometries() {
	}

	public static IndexGeometry exactlyOne(SourceGeometryLiteral sourceGeometryLiteral) {
		List<IndexGeometry> geometries = IndexGeometry.fromSourceGeometryLiteralComponents(sourceGeometryLiteral);
		assertEquals("Expected exactly one index geometry", 1, geometries.size());
		return geometries.get(0);
	}

	public static IndexGeometry exactlyOne(String wkt) {
		return exactlyOne(SourceGeometryLiteral.fromWkt(wkt));
	}
}
