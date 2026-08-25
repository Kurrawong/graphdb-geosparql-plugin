package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.datatype.GeometryDatatype;
import org.apache.jena.geosparql.implementation.jts.CustomCoordinateSequence;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.Test;
import org.locationtech.jts.geom.Point;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Verifies strict, reusable GeoJSON source geometry literals from
 * <a href="https://github.com/Kurrawong/graphdb-geosparql-plugin/issues/12">issue 12</a>.
 */
public class GeoJsonLiteralTest {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();

	@Test
	public void geoJsonPointIsRegisteredAsCrs84SourceGeometryLiteral() {
		Literal literal = VALUE_FACTORY.createLiteral(
				"{\"type\":\"Point\",\"coordinates\":[24.5,41.4]}",
				GeoConstants.GEO_JSON_LITERAL);

		SourceGeometryLiteral source = JenaGeometryAdapter.toSourceGeometryLiteral(literal);
		Point point = (Point) source.asGeometryWrapper().getParsingGeometry();

		assertSame(GeoJsonGeometryDatatype.INSTANCE,
				GeometryDatatype.get(GeoConstants.GEO_JSON_LITERAL.stringValue()));
		assertEquals(GeoConstants.GEO_JSON_LITERAL, source.datatype());
		assertEquals(GeoConstants.GEO_JSON_LITERAL, source.jenaDatatype());
		assertEquals(SRS_URI.DEFAULT_WKT_CRS84, source.effectiveCrsUri());
		assertEquals(24.5, point.getX(), 0.0);
		assertEquals(41.4, point.getY(), 0.0);
		assertTrue(point.getCoordinateSequence() instanceof CustomCoordinateSequence);
	}

	@Test
	public void everyGeoJsonGeometryRootParses() {
		Map<String, String> geometries = new LinkedHashMap<>();
		geometries.put("Point", "{\"type\":\"Point\",\"coordinates\":[1,2]}");
		geometries.put("MultiPoint", "{\"type\":\"MultiPoint\",\"coordinates\":[[1,2],[3,4]]}");
		geometries.put("LineString", "{\"type\":\"LineString\",\"coordinates\":[[1,2],[3,4]]}");
		geometries.put("MultiLineString", "{\"type\":\"MultiLineString\",\"coordinates\":[[[1,2],[3,4]]]}");
		geometries.put("Polygon", "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[0,2],[2,2],[0,0]]]}");
		geometries.put("MultiPolygon", "{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0,0],[0,2],[2,2],[0,0]]]]}");
		geometries.put("GeometryCollection", "{\"type\":\"GeometryCollection\",\"geometries\":["
				+ "{\"type\":\"Point\",\"coordinates\":[1,2]},"
				+ "{\"type\":\"GeometryCollection\",\"geometries\":["
				+ "{\"type\":\"LineString\",\"coordinates\":[[1,2],[3,4]]}]}]}");

		for (Map.Entry<String, String> geometry : geometries.entrySet()) {
			SourceGeometryLiteral source = source(geometry.getValue());

			assertEquals(geometry.getKey(), geometry.getKey(),
					source.asGeometryWrapper().getParsingGeometry().getGeometryType());
			assertEquals(geometry.getKey(), 2, source.asGeometryWrapper().getCoordinateDimension());
		}
	}

	@Test
	public void geoJsonResultSerializationIsTwoDimensionalAndOmitsInputMetadata() {
		SourceGeometryLiteral threeDimensional = source("{\"type\":\"LineString\","
				+ "\"coordinates\":[[1,2,10],[3,4,20]],\"bbox\":[1,2,10,3,4,20],"
				+ "\"description\":\"source metadata\"}");
		SourceGeometryLiteral empty = source("");

		Literal dimensionalResult = JenaGeometryAdapter.toRdf4jLiteral(VALUE_FACTORY,
				threeDimensional.asGeometryWrapper(), GeoConstants.GEO_JSON_LITERAL);
		Literal emptyResult = JenaGeometryAdapter.toRdf4jLiteral(VALUE_FACTORY,
				empty.asGeometryWrapper(), GeoConstants.GEO_JSON_LITERAL);

		assertEquals(GeoConstants.GEO_JSON_LITERAL, dimensionalResult.getDatatype());
		assertEquals(2, source(dimensionalResult.stringValue()).asGeometryWrapper().getCoordinateDimension());
		assertFalse(dimensionalResult.stringValue().contains("bbox"));
		assertFalse(dimensionalResult.stringValue().contains("description"));
		assertEquals("{\"type\":\"Point\",\"coordinates\":[]}", emptyResult.stringValue());
	}

	@Test
	public void threeDimensionalCollectionCanContainEmptyGeometryAndMatchingBbox() {
		SourceGeometryLiteral source = source("{\"type\":\"GeometryCollection\","
				+ "\"geometries\":[{\"type\":\"Point\",\"coordinates\":[]},"
				+ "{\"type\":\"LineString\",\"coordinates\":[[1,2,10],[3,4,20]]}],"
				+ "\"bbox\":[1,2,10,3,4,20],\"foreignMember\":true}");

		assertEquals(3, source.asGeometryWrapper().getCoordinateDimension());
		assertEquals(10.0, source.asGeometryWrapper().getParsingGeometry()
				.getGeometryN(1).getCoordinate().getZ(), 0.0);
	}

	@Test
	public void invalidGeoJsonGeometryLiteralsAreRejected() {
		List<String> invalid = List.of(
				"[]",
				"{",
				"{}",
				"{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[1,2]}}",
				"{\"type\":\"FeatureCollection\",\"features\":[]}",
				"{\"type\":\"Unknown\",\"coordinates\":[]}",
				"{\"type\":\"Point\",\"coordinates\":[1,2],\"crs\":null}",
				"{\"type\":\"Point\",\"coordinates\":null}",
				"{\"type\":\"Point\",\"coordinates\":[1]}",
				"{\"type\":\"Point\",\"coordinates\":[1,2,3,4]}",
				"{\"type\":\"Point\",\"coordinates\":[1,null]}",
				"{\"type\":\"Point\",\"coordinates\":[1,\"2\"]}",
				"{\"type\":\"Point\",\"coordinates\":[1E400,2]}",
				"{\"type\":\"Point\",\"coordinates\":[181,2]}",
				"{\"type\":\"Point\",\"coordinates\":[1,91]}",
				"{\"type\":\"LineString\",\"coordinates\":[[1,2]]}",
				"{\"type\":\"LineString\",\"coordinates\":[[1,2],[3,4,5]]}",
				"{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[0,2],[2,2],[1,0]]]}",
				"{\"type\":\"GeometryCollection\",\"geometries\":[null]}",
				"{\"geometry\":null}",
				"{\"type\":\"GeometryCollection\",\"geometries\":[],\"coordinates\":null}",
				"{\"type\":\"Point\",\"coordinates\":[1,2],\"bbox\":[0,0,1]}",
				"{\"type\":\"Point\",\"coordinates\":[1,2,3],\"bbox\":[0,0,1,1]}",
				"{\"type\":\"Point\",\"coordinates\":[1,2],\"bbox\":[0,null,1,2]}",
				"{\"type\":\"Point\",\"coordinates\":[1,2],\"bbox\":[0,0,1E400,2]}"
		);

		for (String lexicalForm : invalid) {
			JenaGeoSparqlException exception = assertThrows(JenaGeoSparqlException.class,
					() -> source(lexicalForm).asGeometryWrapper());
			assertTrue(lexicalForm, exception.getMessage().contains("Invalid GeoSPARQL geometry literal"));
		}
	}

	@Test
	public void zeroLengthGeoJsonIsCanonicalEmptyPointAcrossEvaluationAndSerialization() throws Exception {
		Literal emptyLiteral = VALUE_FACTORY.createLiteral("", GeoConstants.GEO_JSON_LITERAL);
		Literal pointLiteral = VALUE_FACTORY.createLiteral(
				"{\"type\":\"Point\",\"coordinates\":[1,2]}", GeoConstants.GEO_JSON_LITERAL);
		SourceGeometryLiteral empty = source("");
		IndexGeometry sentinel = JenaGeometryAdapter.toIndexGeometry(empty);

		assertEquals("Point", empty.asGeometryWrapper().getGeometryType());
		assertEquals(VALUE_FACTORY.createLiteral(true), evaluate(GeoConstants.GEO_IS_EMPTY, emptyLiteral));
		assertEquals(VALUE_FACTORY.createLiteral(0), evaluate(GeoConstants.GEO_DIMENSION, emptyLiteral));
		assertEquals(VALUE_FACTORY.createLiteral(true),
				evaluate(GeoConstants.GEOF_SF_DISJOINT, emptyLiteral, pointLiteral));
		assertEquals(VALUE_FACTORY.createLiteral(false),
				evaluate(GeoConstants.GEOF_SF_INTERSECTS, emptyLiteral, pointLiteral));
		assertFalse(sentinel.isSpatialCandidate());
		assertTrue(sentinel.indexEnvelope().isNull());
		assertEquals("POINT EMPTY", serialize(empty, GeoConstants.GEO_WKT_LITERAL).stringValue());
		assertEquals("", serialize(empty, GeoConstants.GEO_GML_LITERAL).stringValue());
		assertEquals("{\"type\":\"Point\",\"coordinates\":[]}",
				serialize(empty, GeoConstants.GEO_JSON_LITERAL).stringValue());
	}

	@Test
	public void typedEmptyGeoJsonGeometriesAreEmittedAsReusableTypedObjects() {
		Map<String, String> emptyGeometries = new LinkedHashMap<>();
		emptyGeometries.put("Point", "{\"type\":\"Point\",\"coordinates\":[]}");
		emptyGeometries.put("MultiPoint", "{\"type\":\"MultiPoint\",\"coordinates\":[]}");
		emptyGeometries.put("LineString", "{\"type\":\"LineString\",\"coordinates\":[]}");
		emptyGeometries.put("MultiLineString", "{\"type\":\"MultiLineString\",\"coordinates\":[]}");
		emptyGeometries.put("Polygon", "{\"type\":\"Polygon\",\"coordinates\":[]}");
		emptyGeometries.put("MultiPolygon", "{\"type\":\"MultiPolygon\",\"coordinates\":[]}");
		emptyGeometries.put("GeometryCollection", "{\"type\":\"GeometryCollection\",\"geometries\":[]}");

		for (Map.Entry<String, String> emptyGeometry : emptyGeometries.entrySet()) {
			Literal output = serialize(source(emptyGeometry.getValue()), GeoConstants.GEO_JSON_LITERAL);
			SourceGeometryLiteral parsed = source(output.stringValue());

			assertEquals(emptyGeometry.getKey(), emptyGeometry.getKey(),
					parsed.asGeometryWrapper().getGeometryType());
			assertTrue(emptyGeometry.getKey(), parsed.asGeometryWrapper().isEmpty());
		}
	}

	private static Literal serialize(SourceGeometryLiteral source, IRI datatype) {
		return JenaGeometryAdapter.toRdf4jLiteral(VALUE_FACTORY, source.asGeometryWrapper(), datatype);
	}

	private static Value evaluate(IRI function, Value... arguments) throws Exception {
		return JenaFunctionEvaluator.evaluate(VALUE_FACTORY, function.stringValue(), arguments);
	}

	private static SourceGeometryLiteral source(String lexicalForm) {
		return JenaGeometryAdapter.toSourceGeometryLiteral(
				VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_JSON_LITERAL));
	}
}
