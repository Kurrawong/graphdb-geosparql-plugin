package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.function.Function;
import org.eclipse.rdf4j.query.algebra.evaluation.function.FunctionRegistry;
import org.junit.Test;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SimplifyFunctionTest {
	private static final String URI = GeoConstants.NS_GEOF + "simplify";
	private static final String EPSG_4326 = "http://www.opengis.net/def/crs/EPSG/0/4326";
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE =
			new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void simplificationRetainsSourceCrsAndLiteralDatatype() throws Exception {
		Literal line = wkt("<" + EPSG_4326 + "> "
				+ "LINESTRING(10 100,10.1 101,10 102)");

		Literal result = (Literal) evaluate(line, VALUE_FACTORY.createLiteral(0.2));
		GeometryWrapper geometry = GeometryWrapper.extract(result.stringValue(), WKTDatatype.URI);
		LineString simplified = (LineString) geometry.getParsingGeometry();

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertEquals(EPSG_4326, geometry.getSrsURI());
		assertEquals(2, simplified.getNumPoints());
		assertEquals(10.0, simplified.getCoordinateN(0).x, 0.0);
		assertEquals(100.0, simplified.getCoordinateN(0).y, 0.0);
		assertEquals(10.0, simplified.getCoordinateN(1).x, 0.0);
		assertEquals(102.0, simplified.getCoordinateN(1).y, 0.0);
	}

	@Test
	public void simplificationRejectsNegativeAndNonFiniteTolerance() {
		Literal line = wkt("LINESTRING(0 0,1 0.1,2 0)");
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(line, VALUE_FACTORY.createLiteral(-0.1)));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(line, VALUE_FACTORY.createLiteral(Double.NaN)));
	}

	@Test
	public void geoJsonSimplificationRetainsDefinedAltitudeAtSurvivingVertices() throws Exception {
		Literal line = VALUE_FACTORY.createLiteral(
				"{\"type\":\"LineString\",\"coordinates\":[[0,0,5],[1,0.1,6],[2,0,7]]}",
				GeoConstants.GEO_JSON_LITERAL);

		Literal result = (Literal) evaluate(line, VALUE_FACTORY.createLiteral(0.2));
		LineString simplified = (LineString) SourceGeometryLiteral.fromLiteral(result)
				.asGeometryWrapper().getParsingGeometry();

		assertEquals(GeoConstants.GEO_JSON_LITERAL, result.getDatatype());
		assertEquals(2, simplified.getNumPoints());
		assertEquals(5.0, simplified.getCoordinateN(0).getZ(), 0.0);
		assertEquals(7.0, simplified.getCoordinateN(1).getZ(), 0.0);
	}

	@Test
	public void zeroToleranceRetainsMeasuredPointLiteral() throws Exception {
		Literal result = (Literal) evaluate(wkt("POINT M(1 2 7)"), VALUE_FACTORY.createLiteral(0));
		GeometryWrapper geometry = GeometryWrapper.extract(result.stringValue(), WKTDatatype.URI);
		Point point = (Point) geometry.getParsingGeometry();

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertEquals(3, geometry.getCoordinateDimension());
		assertEquals(1, point.getCoordinateSequence().getMeasures());
		assertEquals(7.0, point.getCoordinateSequence().getM(0), 0.0);
	}

	@Test
	public void survivingWktVerticesRetainAltitudeAndMeasureInSourceAxisOrder() throws Exception {
		for (String crs : new String[]{"", "<" + EPSG_4326 + "> "}) {
			Literal source = wkt(crs + "LINESTRING ZM(10 20 3 7,10.1 21 4 8,10 22 5 9)");
			for (double tolerance : new double[]{0, 0.2}) {
				Literal result = (Literal) evaluate(source, VALUE_FACTORY.createLiteral(tolerance));
				GeometryWrapper geometry = GeometryWrapper.extract(result.stringValue(), WKTDatatype.URI);
				LineString line = (LineString) geometry.getParsingGeometry();

				assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
				assertEquals(GeometryWrapper.extract(source.stringValue(), WKTDatatype.URI).getSrsURI(),
						geometry.getSrsURI());
				assertEquals(4, geometry.getCoordinateDimension());
				assertEquals(1, line.getCoordinateSequence().getMeasures());
				assertEquals(tolerance == 0 ? 3 : 2, line.getNumPoints());
				for (int index = 0; index < line.getNumPoints(); index++) {
					int sourceIndex = tolerance == 0 ? index : index * 2;
					assertEquals(sourceIndex == 1 ? 10.1 : 10.0,
							line.getCoordinateSequence().getX(index), 0.0);
					assertEquals(20.0 + sourceIndex, line.getCoordinateSequence().getY(index), 0.0);
					assertEquals(3.0 + sourceIndex, line.getCoordinateSequence().getZ(index), 0.0);
					assertEquals(7.0 + sourceIndex, line.getCoordinateSequence().getM(index), 0.0);
				}
			}
		}
	}

	@Test
	public void authorityAxisPointLiteralRetainsAltitudeAtZeroTolerance() throws Exception {
		Literal result = (Literal) evaluate(wkt("<" + EPSG_4326 + "> POINT Z(10 100 3)"),
				VALUE_FACTORY.createLiteral(0));
		GeometryWrapper geometry = GeometryWrapper.extract(result.stringValue(), WKTDatatype.URI);
		Point point = (Point) geometry.getParsingGeometry();

		assertEquals(EPSG_4326, geometry.getSrsURI());
		assertEquals(3, geometry.getCoordinateDimension());
		assertEquals(10.0, point.getX(), 0.0);
		assertEquals(100.0, point.getY(), 0.0);
		assertEquals(3.0, point.getCoordinateSequence().getZ(0), 0.0);
	}

	@Test
	public void polygonRepairWithUndefinedOrdinatesProducesExpressionError() {
		Literal source = wkt("POLYGON ZM((0 0 1 10,2 2 2 20,0 2 3 30,2 0 4 40,0 0 1 10))");

		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(source, VALUE_FACTORY.createLiteral(0)));
	}

	@Test
	public void measuredPolygonLiteralRetainsSurvivingVertexOrdinates() throws Exception {
		Literal source = wkt("POLYGON ZM((0 0 1 10,1 0.1 2 20,2 0 3 30,2 2 4 40,0 2 5 50,0 0 1 10))");

		Literal result = (Literal) evaluate(source, VALUE_FACTORY.createLiteral(0.2));
		GeometryWrapper geometry = GeometryWrapper.extract(result.stringValue(), WKTDatatype.URI);
		var coordinates = ((Polygon) geometry.getParsingGeometry()).getExteriorRing().getCoordinateSequence();

		assertEquals(4, geometry.getCoordinateDimension());
		assertEquals(5, coordinates.size());
		double[] expectedZ = {1, 3, 4, 5, 1};
		for (int i = 0; i < coordinates.size(); i++) {
			assertEquals(expectedZ[i], coordinates.getZ(i), 0.0);
			assertEquals(expectedZ[i] * 10, coordinates.getM(i), 0.0);
		}
	}

	@Test
	public void planarPolygonRepairReturnsAValidXyLiteral() throws Exception {
		Literal result = (Literal) evaluate(wkt("POLYGON((0 0,2 2,0 2,2 0,0 0))"),
				VALUE_FACTORY.createLiteral(0));
		GeometryWrapper geometry = GeometryWrapper.extract(result.stringValue(), WKTDatatype.URI);

		assertTrue(geometry.getParsingGeometry().isValid());
		assertEquals(1.0, geometry.getParsingGeometry().getArea(), 0.0);
		assertEquals(2, geometry.getCoordinateDimension());
	}

	@Test
	public void coincidentSurvivingVerticesRetainTheirOwnMeasures() throws Exception {
		Literal source = wkt("LINESTRING M(0 0 1,1 0.1 2,0 0 3)");

		for (double tolerance : new double[]{0, 0.2}) {
			Literal literal = (Literal) evaluate(source, VALUE_FACTORY.createLiteral(tolerance));
			LineString line = (LineString) SourceGeometryLiteral.fromLiteral(literal)
					.asGeometryWrapper().getParsingGeometry();
			assertEquals(3, line.getNumPoints());
			assertEquals(1.0, line.getCoordinateSequence().getM(0), 0.0);
			assertEquals(2.0, line.getCoordinateSequence().getM(1), 0.0);
			assertEquals(3.0, line.getCoordinateSequence().getM(2), 0.0);
		}
	}

	@Test
	public void coincidentEndpointMeasuresSurviveRemovalOfOtherVertices() throws Exception {
		Literal source = wkt("LINESTRING M(0 0 1,1 0.1 2,2 0 3,1 1 4,0 0 5)");

		Literal literal = (Literal) evaluate(source, VALUE_FACTORY.createLiteral(0.2));
		LineString line = (LineString) SourceGeometryLiteral.fromLiteral(literal)
				.asGeometryWrapper().getParsingGeometry();

		assertEquals(4, line.getNumPoints());
		double[] expected = {1, 3, 4, 5};
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], line.getCoordinateSequence().getM(i), 0.0);
		}
	}

	@Test
	public void coincidentCollectionMembersRetainTheirOwnMeasures() throws Exception {
		Literal source = wkt("GEOMETRYCOLLECTION M(POINT M(0 0 1),POINT M(0 0 2),"
				+ "MULTILINESTRING M((0 0 3,1 0.1 4,2 0 5),(0 0 6,1 -0.1 7,2 0 8)))");

		Literal literal = (Literal) evaluate(source, VALUE_FACTORY.createLiteral(0.2));
		GeometryCollection result = (GeometryCollection) SourceGeometryLiteral.fromLiteral(literal)
				.asGeometryWrapper().getParsingGeometry();

		assertEquals(3, result.getNumGeometries());
		assertEquals(1.0, ((Point) result.getGeometryN(0)).getCoordinateSequence().getM(0), 0.0);
		assertEquals(2.0, ((Point) result.getGeometryN(1)).getCoordinateSequence().getM(0), 0.0);
		GeometryCollection lines = (GeometryCollection) result.getGeometryN(2);
		for (int i = 0; i < 2; i++) {
			var coordinates = ((LineString) lines.getGeometryN(i)).getCoordinateSequence();
			assertEquals(2, coordinates.size());
			assertEquals(3.0 + i * 3, coordinates.getM(0), 0.0);
			assertEquals(5.0 + i * 3, coordinates.getM(1), 0.0);
		}
	}

	@Test
	public void measuredCollectionMemberDoesNotRestrictPlanarPolygonRepair() throws Exception {
		Literal source = wkt("GEOMETRYCOLLECTION(POINT M(10 20 7),POLYGON((0 0,2 2,0 2,2 0,0 0)))");

		Literal literal = (Literal) evaluate(source, VALUE_FACTORY.createLiteral(0));
		GeometryCollection result = (GeometryCollection) SourceGeometryLiteral.fromLiteral(literal)
				.asGeometryWrapper().getParsingGeometry();

		assertEquals(7.0, ((Point) result.getGeometryN(0)).getCoordinateSequence().getM(0), 0.0);
		assertTrue(result.getGeometryN(1).isValid());
		assertEquals(1.0, result.getGeometryN(1).getArea(), 0.0);
	}

	@Test
	public void polygonRepairRetainsMeasuresWhenNoNewVerticesAreNeeded() throws Exception {
		Literal source = wkt("POLYGON M((0 0 1,4 0 5,4 4 9,0 4 5,0 0 1),"
				+ "(5 5 11,6 5 12,6 6 13,5 6 12,5 5 11))");

		Literal literal = (Literal) evaluate(source, VALUE_FACTORY.createLiteral(0));
		Polygon polygon = (Polygon) SourceGeometryLiteral.fromLiteral(literal).asGeometryWrapper().getParsingGeometry();

		assertTrue(polygon.isValid());
		assertEquals(0, polygon.getNumInteriorRing());
		assertEquals(16.0, polygon.getArea(), 0.0);
		var coordinates = polygon.getExteriorRing().getCoordinateSequence();
		assertEquals(1, coordinates.getMeasures());
		for (int i = 0; i < coordinates.size(); i++) {
			assertEquals(coordinates.getX(i) + coordinates.getY(i) + 1, coordinates.getM(i), 0.0);
		}
	}

	@Test
	public void gmlSimplificationRetainsDeclaredXyzLayoutWithUndefinedAltitude() throws Exception {
		for (String tailAltitude : new String[]{"5", "NaN"}) {
			Literal source = VALUE_FACTORY.createLiteral(
					"<g:LineString xmlns:g=\"http://www.opengis.net/gml/3.2\" "
							+ "srsName=\"http://www.opengis.net/def/crs/EPSG/0/4979\" srsDimension=\"3\">"
							+ "<g:posList>0 0 NaN 1 0.1 4 2 0 " + tailAltitude + "</g:posList></g:LineString>",
					GeoConstants.GEO_GML_LITERAL);

			Literal result = (Literal) evaluate(source, VALUE_FACTORY.createLiteral(0.2));
			GeometryWrapper geometry = SourceGeometryLiteral.fromLiteral(result).asGeometryWrapper();
			var coordinates = ((LineString) geometry.getParsingGeometry()).getCoordinateSequence();

			assertEquals(GeoConstants.GEO_GML_LITERAL, result.getDatatype());
			assertEquals("http://www.opengis.net/def/crs/EPSG/0/4979", geometry.getSrsURI());
			assertEquals(3, geometry.getCoordinateDimension());
			assertEquals(3, coordinates.getDimension());
			assertEquals(0, coordinates.getMeasures());
			assertEquals(2, coordinates.size());
			assertTrue(Double.isNaN(coordinates.getZ(0)));
			assertEquals(Double.parseDouble(tailAltitude), coordinates.getZ(1), 0.0);
		}
	}

	@Test
	public void collapsedMeasuredPolygonRetainsTypedEmptyLiteral() throws Exception {
		Literal source = wkt("POLYGON M((0 0 1,2 0 2,2 2 3,0 2 4,0 0 1))");

		Literal result = (Literal) evaluate(source, VALUE_FACTORY.createLiteral(100));
		GeometryWrapper geometry = SourceGeometryLiteral.fromLiteral(result).asGeometryWrapper();

		assertTrue(geometry.isEmpty());
		assertEquals("Polygon", geometry.getGeometryType());
		assertEquals(2, geometry.getTopologicalDimension());
		assertEquals(3, geometry.getCoordinateDimension());
	}

	private Value evaluate(Value... args) throws ValueExprEvaluationException {
		return registeredFunction().evaluate(TRIPLE_SOURCE, args);
	}

	private Function registeredFunction() {
		GeoSparqlFunctionRegistration.registerAll();
		return FunctionRegistry.getInstance().get(URI)
				.orElseThrow(() -> new AssertionError("Function not registered: " + URI));
	}

	private Literal wkt(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL);
	}
}
