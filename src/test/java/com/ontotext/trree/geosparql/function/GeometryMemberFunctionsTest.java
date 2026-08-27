package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.GeoJsonResultDimensionPolicy;
import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.function.Function;
import org.eclipse.rdf4j.query.algebra.evaluation.function.FunctionRegistry;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GeometryMemberFunctionsTest {
	private static final String GEOMETRY_N_URI = GeoConstants.NS_GEOF + "geometryN";
	private static final String NUM_GEOMETRIES_URI = GeoConstants.NS_GEOF + "numGeometries";
	private static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE = new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void numGeometriesCountsAtomicAndDirectCollectionMembers() throws Exception {
		assertIntegerResult(BigInteger.ONE, evaluate(NUM_GEOMETRIES_URI, wkt("POINT(1 2)")));
		assertIntegerResult(BigInteger.valueOf(2), evaluate(NUM_GEOMETRIES_URI,
				wkt("GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(3 4,5 6))")));
	}

	@Test
	public void geometryNUsesOneBasedIndexAndPreservesWktCoordinateLayout() throws Exception {
		Literal xyzResult = (Literal) evaluate(GEOMETRY_N_URI,
				wkt("MULTIPOINT Z ((1 2 3),(4 5 6))"), VALUE_FACTORY.createLiteral(2));
		Literal xymResult = (Literal) evaluate(GEOMETRY_N_URI,
				wkt("GEOMETRYCOLLECTION M (POINT M(1 2 3),POINT M(4 5 6))"),
				VALUE_FACTORY.createLiteral(2));

		assertEquals(GeoConstants.GEO_WKT_LITERAL, xyzResult.getDatatype());
		assertEquals("POINT Z(4 5 6)", xyzResult.stringValue());
		assertEquals("POINT M(4 5 6)", xymResult.stringValue());
	}

	@Test
	public void geometryNRetainsTheSelectedMixedCollectionMemberLayout() throws Exception {
		Literal source = wkt("GEOMETRYCOLLECTION Z(POINT Z(1 2 3),POINT M(4 5 6))");

		Literal xyzResult = (Literal) evaluate(
				GEOMETRY_N_URI, source, VALUE_FACTORY.createLiteral(1));
		Literal xymResult = (Literal) evaluate(
				GEOMETRY_N_URI, source, VALUE_FACTORY.createLiteral(2));

		assertEquals("POINT Z(1 2 3)", xyzResult.stringValue());
		assertEquals("POINT M(4 5 6)", xymResult.stringValue());
	}

	@Test
	public void numGeometriesRetainsAtomicMultiAndCollectionEmptyCounts() throws Exception {
		for (String atomic : List.of("POINT EMPTY", "LINESTRING EMPTY", "POLYGON EMPTY")) {
			assertIntegerResult(BigInteger.ONE, evaluate(NUM_GEOMETRIES_URI, wkt(atomic)));
		}
		for (String collection : List.of("MULTIPOINT EMPTY", "MULTILINESTRING EMPTY",
				"MULTIPOLYGON EMPTY", "GEOMETRYCOLLECTION EMPTY")) {
			assertIntegerResult(BigInteger.ZERO, evaluate(NUM_GEOMETRIES_URI, wkt(collection)));
		}
		assertIntegerResult(BigInteger.valueOf(2), evaluate(NUM_GEOMETRIES_URI,
				geoJson("{\"type\":\"GeometryCollection\",\"geometries\":["
						+ "{\"type\":\"Point\",\"coordinates\":[]},"
						+ "{\"type\":\"LineString\",\"coordinates\":[]}]}")));
	}

	@Test
	public void numGeometriesSupportsGmlAndGeoJsonGeometryLiterals() throws Exception {
		Literal gml = gmlFromWkt("<" + EPSG_32634 + "> MULTIPOINT((1 2),(3 4))");
		Literal geoJson = geoJson("{\"type\":\"GeometryCollection\",\"geometries\":["
				+ "{\"type\":\"Point\",\"coordinates\":[1,2]},"
				+ "{\"type\":\"LineString\",\"coordinates\":[[3,4],[5,6]]}]}");

		assertIntegerResult(BigInteger.valueOf(2), evaluate(NUM_GEOMETRIES_URI, gml));
		assertIntegerResult(BigInteger.valueOf(2), evaluate(NUM_GEOMETRIES_URI, geoJson));
	}

	@Test
	public void geometryNReturnsAtomicGeometryAndDirectNestedMember() throws Exception {
		Literal atomic = (Literal) evaluate(GEOMETRY_N_URI,
				wkt("LINESTRING(1 2,3 4)"), VALUE_FACTORY.createLiteral(1));
		Literal nested = (Literal) evaluate(GEOMETRY_N_URI,
				wkt("GEOMETRYCOLLECTION(GEOMETRYCOLLECTION(POINT(1 2)),POINT(3 4))"),
				VALUE_FACTORY.createLiteral(1));

		assertEquals("LineString", parsed(atomic).asGeometryWrapper().getGeometryType());
		assertEquals("GeometryCollection", parsed(nested).asGeometryWrapper().getGeometryType());
		assertEquals(1, parsed(nested).asGeometryWrapper().getXYGeometry().getNumGeometries());
	}

	@Test
	public void geometryNPreservesAnEmptyDirectMember() throws Exception {
		Literal result = (Literal) evaluate(GEOMETRY_N_URI,
				geoJson("{\"type\":\"GeometryCollection\",\"geometries\":["
						+ "{\"type\":\"Point\",\"coordinates\":[]},"
						+ "{\"type\":\"LineString\",\"coordinates\":[[1,2,3],[3,4,5]]}]}"),
				VALUE_FACTORY.createLiteral(1));

		assertEquals("Point", parsed(result).asGeometryWrapper().getGeometryType());
		assertTrue(parsed(result).asGeometryWrapper().isEmpty());
		assertEquals(2, parsed(result).asGeometryWrapper().getCoordinateDimension());
	}

	@Test
	public void geometryNRetainsProjectedGmlDatatypeAndSrs() throws Exception {
		Literal source = gmlFromWkt("<" + EPSG_32634 + "> MULTIPOINT((799997.8 4589779.63),"
				+ "(700000 4500000))");

		Literal result = (Literal) evaluate(GEOMETRY_N_URI, source, VALUE_FACTORY.createLiteral(1));
		SourceGeometryLiteral parsed = parsed(result);

		assertEquals(GeoConstants.GEO_GML_LITERAL, result.getDatatype());
		assertEquals(EPSG_32634, parsed.effectiveCrsUri());
		assertEquals("Point", parsed.asGeometryWrapper().getGeometryType());
		assertEquals(799997.8, parsed.asGeometryWrapper().getXYGeometry().getCoordinate().x, 0.0);
		assertEquals(4589779.63, parsed.asGeometryWrapper().getXYGeometry().getCoordinate().y, 0.0);
	}

	@Test
	public void geometryNPreservesNativeGeoJsonCoordinateLayout() throws Exception {
		Literal source = geoJson("{\"type\":\"GeometryCollection\",\"geometries\":["
				+ "{\"type\":\"Point\",\"coordinates\":[1,2,3]},"
				+ "{\"type\":\"Point\",\"coordinates\":[4,5,6]}]}");

		Literal result = (Literal) evaluate(GEOMETRY_N_URI, source, VALUE_FACTORY.createLiteral(2));

		assertEquals(GeoConstants.GEO_JSON_LITERAL, result.getDatatype());
		assertEquals("{\"type\":\"Point\",\"coordinates\":[4,5,6]}", result.stringValue());
		assertEquals(CRS84, parsed(result).effectiveCrsUri());
		assertEquals(3, parsed(result).asGeometryWrapper().getCoordinateDimension());

		Literal xyResult = (Literal) evaluate(GEOMETRY_N_URI,
				geoJson("{\"type\":\"MultiPoint\",\"coordinates\":[[1,2],[4,5]]}"),
				VALUE_FACTORY.createLiteral(1));
		assertEquals("{\"type\":\"Point\",\"coordinates\":[1,2]}", xyResult.stringValue());
		assertEquals(2, parsed(xyResult).asGeometryWrapper().getCoordinateDimension());
	}

	@Test
	public void geometryNResultBoundaryRejectsRequiredAltitudeLoss() {
		QueryFunctionManifest.Entry entry = new QueryFunctionManifest.Entry(GEOMETRY_N_URI, 2,
				new QueryFunctionManifest.GeometryMemberProvider(
						(geometry, index) -> geometry.envelope(),
						GeoJsonResultDimensionPolicy.PRESERVE_DEFINED_Z));
		Function function = new QueryFunctionRdf4jAdapter(entry);
		Literal source = geoJson("{\"type\":\"Point\",\"coordinates\":[1,2,3]}");

		assertThrows(ValueExprEvaluationException.class,
				() -> function.evaluate(TRIPLE_SOURCE, source, VALUE_FACTORY.createLiteral(1)));
	}

	@Test
	public void geometryNRetainsZeroLengthGmlCanonicalization() throws Exception {
		Literal result = (Literal) evaluate(GEOMETRY_N_URI,
				VALUE_FACTORY.createLiteral("", GeoConstants.GEO_GML_LITERAL),
				VALUE_FACTORY.createLiteral(1));

		assertEquals(GeoConstants.GEO_GML_LITERAL, result.getDatatype());
		assertEquals("", result.stringValue());
	}

	@Test
	public void geometryNAcceptsOnlyLosslesslyIntegralNumericIndices() throws Exception {
		Literal source = wkt("MULTIPOINT((1 2),(3 4))");
		Literal decimalResult = (Literal) evaluate(GEOMETRY_N_URI, source,
				VALUE_FACTORY.createLiteral(new BigDecimal("2.0")));
		Literal doubleResult = (Literal) evaluate(GEOMETRY_N_URI, source,
				VALUE_FACTORY.createLiteral(2.0d));

		assertEquals("POINT(3 4)", decimalResult.stringValue());
		assertEquals("POINT(3 4)", doubleResult.stringValue());

		List<Value> invalidIndices = List.of(
				VALUE_FACTORY.createLiteral("1"),
				VALUE_FACTORY.createIRI("http://example.com/index/1"),
				VALUE_FACTORY.createLiteral(new BigDecimal("1.5")),
				VALUE_FACTORY.createLiteral("1e0", XSD.DECIMAL),
				VALUE_FACTORY.createLiteral("2147483648", XSD.INTEGER),
				VALUE_FACTORY.createLiteral(Double.NaN),
				VALUE_FACTORY.createLiteral(Double.POSITIVE_INFINITY),
				VALUE_FACTORY.createLiteral(0),
				VALUE_FACTORY.createLiteral(-1),
				VALUE_FACTORY.createLiteral(3));
		for (Value invalidIndex : invalidIndices) {
			assertThrows(invalidIndex.toString(), ValueExprEvaluationException.class,
					() -> evaluate(GEOMETRY_N_URI, source, invalidIndex));
		}
	}

	@Test
	public void geometryNUsesFloatNumericValueForIntegralIndex() throws Exception {
		Literal source = wkt("MULTIPOINT((1 2),(3 4))");
		Literal result = (Literal) evaluate(GEOMETRY_N_URI, source,
				VALUE_FACTORY.createLiteral("1.00000001", XSD.FLOAT));

		assertEquals("POINT(1 2)", result.stringValue());
	}

	@Test
	public void geometryNUsesDoubleNumericValueForIntegralIndex() throws Exception {
		Literal source = wkt("MULTIPOINT((1 2),(3 4))");
		Literal result = (Literal) evaluate(GEOMETRY_N_URI, source,
				VALUE_FACTORY.createLiteral("1.00000000000000001", XSD.DOUBLE));

		assertEquals("POINT(1 2)", result.stringValue());
	}

	@Test
	public void geometryMemberFunctionsEnforceRdfArgumentsAndMandatoryArity() {
		Literal source = wkt("POINT(1 2)");
		Value geometryIri = VALUE_FACTORY.createIRI("http://example.com/geometry");

		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(NUM_GEOMETRIES_URI));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(NUM_GEOMETRIES_URI, source, VALUE_FACTORY.createLiteral(1)));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(NUM_GEOMETRIES_URI, geometryIri));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(NUM_GEOMETRIES_URI, wkt("not geometry")));

		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOMETRY_N_URI));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOMETRY_N_URI, source));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOMETRY_N_URI, source, VALUE_FACTORY.createLiteral(1), source));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOMETRY_N_URI, geometryIri, VALUE_FACTORY.createLiteral(1)));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOMETRY_N_URI, wkt("not geometry"), VALUE_FACTORY.createLiteral(1)));
	}

	@Test
	public void geometryMemberFunctionsHaveManifestAritiesAndTypedProviders() {
		QueryFunctionManifest.Entry geometryN = manifestEntry(GEOMETRY_N_URI);
		QueryFunctionManifest.Entry numGeometries = manifestEntry(NUM_GEOMETRIES_URI);

		assertEquals(2, geometryN.mandatoryArity());
		assertTrue(geometryN.provider() instanceof QueryFunctionManifest.GeometryMemberProvider);
		assertEquals(GeoJsonResultDimensionPolicy.PRESERVE_DEFINED_Z,
				((QueryFunctionManifest.GeometryMemberProvider) geometryN.provider())
						.geoJsonResultDimensionPolicy());
		assertEquals(1, numGeometries.mandatoryArity());
		assertTrue(numGeometries.provider() instanceof QueryFunctionManifest.UnaryGeometryIntegerProvider);
	}

	private Value evaluate(String functionUri, Value... args) throws ValueExprEvaluationException {
		GeoSparqlFunctionRegistration.registerAll();
		Function function = FunctionRegistry.getInstance().get(functionUri)
				.orElseThrow(() -> new AssertionError("Function not registered: " + functionUri));
		return function.evaluate(TRIPLE_SOURCE, args);
	}

	private Literal wkt(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL);
	}

	private Literal geoJson(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_JSON_LITERAL);
	}

	private Literal gmlFromWkt(String lexicalForm) {
		SourceGeometryLiteral source = JenaGeometryAdapter.toSourceGeometryLiteral(wkt(lexicalForm));
		return JenaGeometryAdapter.toRdf4jLiteral(VALUE_FACTORY,
				source.asGeometryWrapper(), GeoConstants.GEO_GML_LITERAL);
	}

	private SourceGeometryLiteral parsed(Literal literal) {
		return JenaGeometryAdapter.toSourceGeometryLiteral(literal);
	}

	private QueryFunctionManifest.Entry manifestEntry(String uri) {
		return QueryFunctionManifest.entries().stream()
				.filter(entry -> uri.equals(entry.uri()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Manifest entry not found: " + uri));
	}

	private void assertIntegerResult(BigInteger expected, Value result) {
		assertTrue(result instanceof Literal);
		assertEquals(expected, ((Literal) result).integerValue());
		assertEquals(XSD.INTEGER, ((Literal) result).getDatatype());
	}
}
