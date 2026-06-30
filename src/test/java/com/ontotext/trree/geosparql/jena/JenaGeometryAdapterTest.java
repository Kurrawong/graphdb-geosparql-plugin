package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.GeoSparqlFunction;
import com.useekm.indexing.GeoConstants;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;

import static org.junit.Assert.*;

public class JenaGeometryAdapterTest {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";

	@Before
	public void initializeAdapter() {
		JenaGeometryAdapter.initialize();
	}

	@Test
	public void wktWithoutExplicitCrsUsesCrs84AndPreservesLexicalForm() {
		SourceGeometryLiteral geometry = SourceGeometryLiteral.fromWkt("POINT(1 2)");

		assertEquals("POINT(1 2)", geometry.lexicalForm());
		assertEquals(GeoConstants.GEO_WKT_LITERAL, geometry.datatype());
		assertEquals(GeoConstants.GEO_WKT_LITERAL, geometry.jenaDatatype());
		assertFalse(geometry.explicitCrsUri().isPresent());
		assertEquals(SRS_URI.DEFAULT_WKT_CRS84, geometry.asGeometryWrapper().getSrsURI());
	}

	@Test
	public void wktWithExplicitCrsPreservesStoredCrsMetadata() {
		SourceGeometryLiteral geometry = SourceGeometryLiteral.fromWkt("<" + CRS84 + "> POINT(1 2)");

		assertEquals(CRS84, geometry.explicitCrsUri().get());
		assertEquals(SRS_URI.DEFAULT_WKT_CRS84, geometry.asGeometryWrapper().getSrsURI());
	}

	@Test
	public void plainStringLiteralCanUseWktFallbackDatatype() {
		Literal literal = VALUE_FACTORY.createLiteral("POINT(3 4)", XMLSchema.STRING);

		SourceGeometryLiteral geometry = JenaGeometryAdapter.toSourceGeometryLiteral(literal,
				GeoConstants.GEO_WKT_LITERAL);

		assertEquals(GeoConstants.GEO_WKT_LITERAL, geometry.datatype());
		assertEquals(SRS_URI.DEFAULT_WKT_CRS84, geometry.asGeometryWrapper().getSrsURI());
	}

	@Test
	public void gmlLiteralPreservesSrsNameAndParses() {
		String gml = "<gml:Point xmlns:gml=\"http://www.opengis.net/gml/3.2\" srsName=\"" + CRS84
				+ "\"><gml:pos>1 2</gml:pos></gml:Point>";
		Literal literal = VALUE_FACTORY.createLiteral(gml, GeoConstants.GEO_GML_LITERAL);

		SourceGeometryLiteral geometry = JenaGeometryAdapter.toSourceGeometryLiteral(literal);

		assertEquals(CRS84, geometry.explicitCrsUri().get());
		assertEquals(GeoConstants.GEO_GML_LITERAL, geometry.datatype());
		assertEquals(SRS_URI.DEFAULT_WKT_CRS84, geometry.asGeometryWrapper().getSrsURI());
	}

	@Test
	public void projectedWktIsTransformedToCrs84IndexGeometry() {
		SourceGeometryLiteral source = SourceGeometryLiteral.fromWkt(
				"<" + EPSG_32634 + "> POINT(799997.80 4589779.63)");

		IndexGeometry index = IndexGeometry.fromSourceGeometryLiteral(source);
		Coordinate coordinate = index.indexGeometry().getCoordinate();

		assertEquals(EPSG_32634, source.explicitCrsUri().get());
		assertEquals(IndexGeometry.INDEX_CRS, index.indexCrs());
		assertEquals(IndexGeometry.BUILD_MODE_TRANSFORMED_GEOMETRY, index.indexBuildMode());
		assertTrue(coordinate.x >= -180 && coordinate.x <= 180);
		assertTrue(coordinate.y >= -90 && coordinate.y <= 90);
	}

	@Test
	public void crs84CoordinatesOutsideDomainFail() {
		JenaGeoSparqlException exception = assertThrows(JenaGeoSparqlException.class,
				() -> SourceGeometryLiteral.fromWkt("POINT(200 200)").asGeometryWrapper());

		assertTrue(exception.getMessage().contains("outside the CRS domain"));
	}

	@Test
	public void unsupportedCrsFailsAsControlledGeoSparqlError() {
		JenaGeoSparqlException exception = assertThrows(JenaGeoSparqlException.class,
				() -> SourceGeometryLiteral.fromWkt("<http://example.com/crs/unknown> POINT(1 2)").asGeometryWrapper());

		assertTrue(exception.getMessage().contains("Unsupported CRS")
				|| exception.getMessage().contains("Invalid GeoSPARQL geometry literal"));
	}

	@Test
	public void unsupportedDatatypeFails() {
		Literal literal = VALUE_FACTORY.createLiteral("POINT(1 2)",
				VALUE_FACTORY.createIRI("http://example.com/geometryLiteral"));

		JenaGeoSparqlException exception = assertThrows(JenaGeoSparqlException.class,
				() -> JenaGeometryAdapter.toSourceGeometryLiteral(literal));

		assertTrue(exception.getMessage().contains("Unsupported GeoSPARQL geometry datatype"));
	}

	@Test
	public void rcc8DisconnectedRequiresAreaTopology() throws Exception {
		SourceGeometryLiteral left = SourceGeometryLiteral.fromWkt("POINT(1 1)");
		SourceGeometryLiteral right = SourceGeometryLiteral.fromWkt("POINT(2 2)");

		assertTrue(JenaFunctionEvaluator.evaluateTopological(GeoConstants.GEOF_SF_DISJOINT.stringValue(), left, right));
		assertFalse(JenaFunctionEvaluator.evaluateTopological(GeoConstants.GEOF_RCC8_DC.stringValue(), left, right));
		assertFalse(GeoSparqlFunction.RCC8_DC.evaluate(left, right));
	}

	@Test
	public void rcc8ExternallyConnectedRequiresAreaTopology() throws Exception {
		SourceGeometryLiteral left = SourceGeometryLiteral.fromWkt("LINESTRING(0 0, 1 1)");
		SourceGeometryLiteral right = SourceGeometryLiteral.fromWkt("LINESTRING(1 1, 2 2)");

		assertTrue(JenaFunctionEvaluator.evaluateTopological(GeoConstants.GEOF_SF_TOUCHES.stringValue(), left, right));
		assertFalse(JenaFunctionEvaluator.evaluateTopological(GeoConstants.GEOF_RCC8_EC.stringValue(), left, right));
		assertFalse(GeoSparqlFunction.RCC8_EC.evaluate(left, right));
	}
}
