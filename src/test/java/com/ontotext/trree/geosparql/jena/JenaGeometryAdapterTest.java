package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.GeoSparqlPropertyRelation;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
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
	private static final String RDF_XML_LITERAL = "http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral";
	private static final String GML_LEGACY_NAMESPACE = "http://www.opengis.net/gml";
	private static final String PROJECTED_POINT_WKT = "<" + EPSG_32634 + "> POINT(799997.80 4589779.63)";
	private static final double PROJECTED_POINT_CRS84_X = 24.5887755;
	private static final double PROJECTED_POINT_CRS84_Y = 41.4035958;
	private static final double CRS84_COORDINATE_TOLERANCE = 1e-6;

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
	public void xmlLiteralCanUseGmlFallbackDatatype() {
		String gml = legacyGmlWithDoubleQuotedNamespaceAndCrs();
		Literal literal = VALUE_FACTORY.createLiteral(gml, VALUE_FACTORY.createIRI(RDF_XML_LITERAL));

		SourceGeometryLiteral geometry = JenaGeometryAdapter.toSourceGeometryLiteral(literal,
				GeoConstants.GEO_GML_LITERAL);

		assertEquals(gml, geometry.lexicalForm());
		assertEquals(GeoConstants.GEO_GML_LITERAL, geometry.datatype());
		assertEquals(CRS84, geometry.explicitCrsUri().get());
		assertEquals(SRS_URI.DEFAULT_WKT_CRS84, geometry.asGeometryWrapper().getSrsURI());
	}

	@Test
	public void xmlLiteralWithoutGmlFallbackIsRejected() {
		Literal literal = VALUE_FACTORY.createLiteral(legacyGmlWithDoubleQuotedNamespaceAndCrs(),
				VALUE_FACTORY.createIRI(RDF_XML_LITERAL));

		JenaGeoSparqlException exception = assertThrows(JenaGeoSparqlException.class,
				() -> JenaGeometryAdapter.toSourceGeometryLiteral(literal));

		assertTrue(exception.getMessage().contains("Unsupported GeoSPARQL geometry datatype"));
	}

	@Test
	public void xmlLiteralCannotUseWktFallbackDatatype() {
		Literal literal = VALUE_FACTORY.createLiteral(legacyGmlWithDoubleQuotedNamespaceAndCrs(),
				VALUE_FACTORY.createIRI(RDF_XML_LITERAL));

		JenaGeoSparqlException exception = assertThrows(JenaGeoSparqlException.class,
				() -> JenaGeometryAdapter.toSourceGeometryLiteral(literal, GeoConstants.GEO_WKT_LITERAL));

		assertTrue(exception.getMessage().contains("Unsupported GeoSPARQL geometry datatype"));
	}

	@Test
	public void legacyGmlNamespaceParsesAndPreservesSourceLexicalForm() {
		String gml = legacyGmlWithDoubleQuotedNamespaceAndCrs();
		Literal literal = VALUE_FACTORY.createLiteral(gml, GeoConstants.GEO_GML_LITERAL);

		SourceGeometryLiteral geometry = JenaGeometryAdapter.toSourceGeometryLiteral(literal);

		assertEquals(gml, geometry.lexicalForm());
		assertTrue(geometry.lexicalForm().contains("xmlns:gml=\"" + GML_LEGACY_NAMESPACE + "\""));
		assertEquals(SRS_URI.DEFAULT_WKT_CRS84, geometry.asGeometryWrapper().getSrsURI());
	}

	@Test
	public void legacyGmlNamespaceWithSingleQuotesParses() {
		String gml = "<gml:Point xmlns:gml='" + GML_LEGACY_NAMESPACE
				+ "'><gml:pos>1 2</gml:pos></gml:Point>";
		Literal literal = VALUE_FACTORY.createLiteral(gml, GeoConstants.GEO_GML_LITERAL);

		SourceGeometryLiteral geometry = JenaGeometryAdapter.toSourceGeometryLiteral(literal);

		assertEquals(gml, geometry.lexicalForm());
		assertEquals(SRS_URI.DEFAULT_WKT_CRS84, geometry.asGeometryWrapper().getSrsURI());
	}

	@Test
	public void projectedWktIsTransformedToCrs84IndexGeometry() {
		SourceGeometryLiteral source = SourceGeometryLiteral.fromWkt(PROJECTED_POINT_WKT);

		IndexGeometry index = IndexGeometry.fromSourceGeometryLiteral(source);
		Coordinate coordinate = index.indexGeometry().getCoordinate();

		assertEquals(EPSG_32634, source.explicitCrsUri().get());
		assertEquals(IndexGeometry.INDEX_CRS, index.indexCrs());
		assertEquals(IndexGeometry.BUILD_MODE_TRANSFORMED_GEOMETRY, index.indexBuildMode());
		assertProjectedPointCrs84Coordinate(coordinate);
	}

	@Test
	public void projectedGmlIsTransformedToCrs84IndexGeometry() {
		String gml = "<gml:Point xmlns:gml=\"http://www.opengis.net/gml/3.2\" srsName=\"" + EPSG_32634
				+ "\"><gml:pos>799997.80 4589779.63</gml:pos></gml:Point>";
		Literal literal = VALUE_FACTORY.createLiteral(gml, GeoConstants.GEO_GML_LITERAL);

		SourceGeometryLiteral source = JenaGeometryAdapter.toSourceGeometryLiteral(literal);
		IndexGeometry index = IndexGeometry.fromSourceGeometryLiteral(source);
		Coordinate coordinate = index.indexGeometry().getCoordinate();

		assertEquals(EPSG_32634, source.explicitCrsUri().get());
		assertEquals(GeoConstants.GEO_GML_LITERAL, source.datatype());
		assertEquals(IndexGeometry.INDEX_CRS, index.indexCrs());
		assertProjectedPointCrs84Coordinate(coordinate);
	}

	@Test
	public void exactEvaluationTransformsMixedCrsArguments() throws Exception {
		Literal crs84Point = VALUE_FACTORY.createLiteral(
				"POINT(" + PROJECTED_POINT_CRS84_X + " " + PROJECTED_POINT_CRS84_Y + ")",
				GeoConstants.GEO_WKT_LITERAL);
		Literal projectedPoint = VALUE_FACTORY.createLiteral(PROJECTED_POINT_WKT, GeoConstants.GEO_WKT_LITERAL);

		Value result = JenaFunctionEvaluator.evaluate(VALUE_FACTORY, GeoConstants.GEOF_DISTANCE.stringValue(),
				crs84Point, projectedPoint, GeoSparqlUnits.URI_METRE);

		assertTrue(result instanceof Literal);
		assertEquals(0d, ((Literal) result).doubleValue(), 0.2d);
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
	public void unsupportedCrsCannotProduceIndexGeometry() {
		JenaGeoSparqlException exception = assertThrows(JenaGeoSparqlException.class,
				() -> IndexGeometry.fromSourceGeometryLiteral(
						SourceGeometryLiteral.fromWkt("<http://example.com/crs/unknown> POINT(1 2)")));

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
		assertFalse(GeoSparqlPropertyRelation.RCC8_DC.evaluate(left, right));
	}

	@Test
	public void rcc8ExternallyConnectedRequiresAreaTopology() throws Exception {
		SourceGeometryLiteral left = SourceGeometryLiteral.fromWkt("LINESTRING(0 0, 1 1)");
		SourceGeometryLiteral right = SourceGeometryLiteral.fromWkt("LINESTRING(1 1, 2 2)");

		assertTrue(JenaFunctionEvaluator.evaluateTopological(GeoConstants.GEOF_SF_TOUCHES.stringValue(), left, right));
		assertFalse(JenaFunctionEvaluator.evaluateTopological(GeoConstants.GEOF_RCC8_EC.stringValue(), left, right));
		assertFalse(GeoSparqlPropertyRelation.RCC8_EC.evaluate(left, right));
	}

	private static String legacyGmlWithDoubleQuotedNamespaceAndCrs() {
		return "<gml:Point xmlns:gml=\"" + GML_LEGACY_NAMESPACE + "\" srsName=\"" + CRS84
				+ "\"><gml:pos>1 2</gml:pos></gml:Point>";
	}

	private static void assertProjectedPointCrs84Coordinate(Coordinate coordinate) {
		assertEquals(PROJECTED_POINT_CRS84_X, coordinate.x, CRS84_COORDINATE_TOLERANCE);
		assertEquals(PROJECTED_POINT_CRS84_Y, coordinate.y, CRS84_COORDINATE_TOLERANCE);
	}
}
