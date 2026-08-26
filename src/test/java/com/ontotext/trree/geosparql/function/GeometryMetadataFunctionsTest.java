package com.ontotext.trree.geosparql.function;

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

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GeometryMetadataFunctionsTest {
	private static final String COORDINATE_DIMENSION_URI =
			"http://www.opengis.net/def/function/geosparql/coordinateDimension";
	private static final String SPATIAL_DIMENSION_URI =
			"http://www.opengis.net/def/function/geosparql/spatialDimension";
	private static final String IS_EMPTY_URI =
			"http://www.opengis.net/def/function/geosparql/isEmpty";
	private static final String IS_SIMPLE_URI =
			"http://www.opengis.net/def/function/geosparql/isSimple";
	private static final String IS_3D_URI =
			"http://www.opengis.net/def/function/geosparql/is3D";
	private static final String IS_MEASURED_URI =
			"http://www.opengis.net/def/function/geosparql/isMeasured";
	private static final String GEOMETRY_TYPE_URI =
			"http://www.opengis.net/def/function/geosparql/geometryType";
	private static final String SF_NAMESPACE = "http://www.opengis.net/ont/sf#";
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE =
			new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void coordinateDimensionUsesCanonicalIriAndReturnsInteger() throws Exception {
		Value result = evaluate(COORDINATE_DIMENSION_URI, wkt("POINT Z(1 2 3)"));

		assertTrue(result instanceof Literal);
		assertEquals(BigInteger.valueOf(3), ((Literal) result).integerValue());
		assertEquals(XSD.INTEGER, ((Literal) result).getDatatype());
	}

	@Test
	public void dimensionFunctionsDistinguishSpatialAndMeasureAxes() throws Exception {
		assertDimensions("POINT(1 2)", 2, 2);
		assertDimensions("POINT Z(1 2 3)", 3, 3);
		assertDimensions("POINT M(1 2 3)", 3, 2);
		assertDimensions("POINT ZM(1 2 3 4)", 4, 3);
	}

	@Test
	public void isEmptyUsesTopologicalEmptinessForCollections() throws Exception {
		assertBooleanResult(true, evaluate(IS_EMPTY_URI, wkt("POINT EMPTY")));
		assertBooleanResult(true, evaluate(IS_EMPTY_URI, wkt("MULTIPOINT EMPTY")));
		assertBooleanResult(true, evaluate(IS_EMPTY_URI,
				wkt("GEOMETRYCOLLECTION(POINT EMPTY,LINESTRING EMPTY)")));
		assertBooleanResult(false, evaluate(IS_EMPTY_URI,
				wkt("GEOMETRYCOLLECTION(POINT EMPTY,POINT(1 2))")));
	}

	@Test
	public void isSimpleRetainsJtsCollectionMemberSemantics() throws Exception {
		assertBooleanResult(true, evaluate(IS_SIMPLE_URI, wkt("POINT(1 2)")));
		assertBooleanResult(false, evaluate(IS_SIMPLE_URI,
				wkt("LINESTRING(0 0,2 2,0 2,2 0)")));
		assertBooleanResult(true, evaluate(IS_SIMPLE_URI,
				wkt("GEOMETRYCOLLECTION(LINESTRING(0 0,2 2),LINESTRING(0 2,2 0))")));
		assertBooleanResult(false, evaluate(IS_SIMPLE_URI,
				wkt("GEOMETRYCOLLECTION(LINESTRING(0 0,2 2,0 2,2 0),POINT(3 3))")));
	}

	@Test
	public void layoutPredicatesUseDeclaredDimensionInfo() throws Exception {
		assertLayout("POINT(1 2)", false, false);
		assertLayout("POINT Z(1 2 3)", true, false);
		assertLayout("POINT M(1 2 3)", false, true);
		assertLayout("POINT ZM(1 2 3 4)", true, true);
	}

	@Test
	public void geometryTypeReturnsSimpleFeaturesClassAnyUri() throws Exception {
		Map<String, String> cases = Map.of(
				"POINT(1 2)", "Point",
				"LINESTRING(0 0,1 1)", "LineString",
				"POLYGON((0 0,0 1,1 1,1 0,0 0))", "Polygon",
				"MULTIPOINT((1 2),(3 4))", "MultiPoint",
				"MULTILINESTRING((0 0,1 1),(2 2,3 3))", "MultiLineString",
				"MULTIPOLYGON(((0 0,0 1,1 1,1 0,0 0)))", "MultiPolygon",
				"GEOMETRYCOLLECTION(POINT(1 2))", "GeometryCollection");

		for (Map.Entry<String, String> geometryCase : cases.entrySet()) {
			Value result = evaluate(GEOMETRY_TYPE_URI, wkt(geometryCase.getKey()));

			assertTrue(result instanceof Literal);
			assertEquals(SF_NAMESPACE + geometryCase.getValue(), result.stringValue());
			assertEquals(XSD.ANYURI, ((Literal) result).getDatatype());
		}
	}

	@Test
	public void geometryTypeRejectsAnUnmappedTopLevelSubtype() {
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GEOMETRY_TYPE_URI, wkt("LINEARRING(0 0,1 0,1 1,0 0)")));
	}

	@Test
	public void typedEmptyAtomicAndMultiGeometriesRetainStructuralMetadata() throws Exception {
		assertEmptyMetadata(new MetadataCase("POINT Z EMPTY", "Point", 3, 3, true, false));
		assertEmptyMetadata(new MetadataCase(
				"MULTIPOINT M EMPTY", "MultiPoint", 3, 2, false, true));
		assertEmptyMetadata(new MetadataCase(
				"MULTILINESTRING Z EMPTY", "MultiLineString", 3, 3, true, false));
		assertEmptyMetadata(new MetadataCase(
				"MULTIPOLYGON ZM EMPTY", "MultiPolygon", 4, 3, true, true));
	}

	@Test
	public void inferredAndMixedCollectionLayoutsRetainJenaDimensionInfo() throws Exception {
		Literal inferred = VALUE_FACTORY.createLiteral(
				"{\"type\":\"Point\",\"coordinates\":[1,2,3]}", GeoConstants.GEO_JSON_LITERAL);
		assertIntegerResult(3, evaluate(COORDINATE_DIMENSION_URI, inferred));
		assertIntegerResult(3, evaluate(SPATIAL_DIMENSION_URI, inferred));
		assertBooleanResult(true, evaluate(IS_3D_URI, inferred));
		assertBooleanResult(false, evaluate(IS_MEASURED_URI, inferred));

		Literal mixed = wkt("GEOMETRYCOLLECTION(POINT Z(1 2 3),POINT M(4 5 6))");
		assertIntegerResult(2, evaluate(COORDINATE_DIMENSION_URI, mixed));
		assertIntegerResult(2, evaluate(SPATIAL_DIMENSION_URI, mixed));
		assertBooleanResult(false, evaluate(IS_3D_URI, mixed));
		assertBooleanResult(false, evaluate(IS_MEASURED_URI, mixed));
	}

	@Test
	public void metadataFunctionsEnforceGeometryArgumentsAndMandatoryArity() {
		Literal point = wkt("POINT(1 2)");
		Value geometryIri = VALUE_FACTORY.createIRI("http://example.com/geometry");

		for (QueryFunctionManifest.Entry entry : QueryFunctionManifest.entries()) {
			if (entry.mandatoryArity() != 1) {
				continue;
			}
			String functionUri = entry.uri();
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, point, point));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, geometryIri));
			assertThrows(functionUri, ValueExprEvaluationException.class,
					() -> evaluate(functionUri, wkt("not geometry")));
		}
	}

	@Test
	public void metadataFunctionsHaveManifestAritiesAndTypedProviders() {
		assertManifestProvider(COORDINATE_DIMENSION_URI,
				QueryFunctionManifest.UnaryGeometryIntegerProvider.class);
		assertManifestProvider(SPATIAL_DIMENSION_URI,
				QueryFunctionManifest.UnaryGeometryIntegerProvider.class);
		assertManifestProvider(IS_EMPTY_URI, QueryFunctionManifest.UnaryGeometryBooleanProvider.class);
		assertManifestProvider(IS_SIMPLE_URI, QueryFunctionManifest.UnaryGeometryBooleanProvider.class);
		assertManifestProvider(IS_3D_URI, QueryFunctionManifest.UnaryGeometryBooleanProvider.class);
		assertManifestProvider(IS_MEASURED_URI, QueryFunctionManifest.UnaryGeometryBooleanProvider.class);
		assertManifestProvider(GEOMETRY_TYPE_URI,
				QueryFunctionManifest.UnaryGeometryAnyUriProvider.class);
	}

	@Test
	public void manifestEntriesRegisterThroughTheTypedAdapter() {
		GeoSparqlFunctionRegistration.registerAll();
		for (QueryFunctionManifest.Entry entry : QueryFunctionManifest.entries()) {
			Function registered = FunctionRegistry.getInstance().get(entry.uri())
					.orElseThrow(() -> new AssertionError("Function not registered: " + entry.uri()));
			assertTrue(entry.uri(), registered instanceof QueryFunctionRdf4jAdapter);
		}
	}

	@Test
	public void legacyMetadataAliasesRemainOutsideTheQueryFunctionManifest() {
		List<String> legacyAliases = List.of(
				GeoConstants.GEO_DIMENSION.stringValue(),
				GeoConstants.GEO_COORDINATE_DIMENSION.stringValue(),
				GeoConstants.GEO_SPATIAL_DIMENSION.stringValue(),
				GeoConstants.GEO_IS_EMPTY.stringValue(),
				GeoConstants.GEO_IS_SIMPLE.stringValue());
		Set<String> manifestUris = QueryFunctionManifest.entries().stream()
				.map(QueryFunctionManifest.Entry::uri)
				.collect(java.util.stream.Collectors.toSet());

		GeoSparqlFunctionRegistration.registerAll();
		for (String legacyAlias : legacyAliases) {
			assertFalse(legacyAlias, manifestUris.contains(legacyAlias));
			Function registered = FunctionRegistry.getInstance().get(legacyAlias)
					.orElseThrow(() -> new AssertionError("Function not registered: " + legacyAlias));
			assertTrue(legacyAlias, registered instanceof GeoSparqlRdf4jFunction);
		}
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

	private QueryFunctionManifest.Entry manifestEntry(String uri) {
		return QueryFunctionManifest.entries().stream()
				.filter(entry -> uri.equals(entry.uri()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Manifest entry not found: " + uri));
	}

	private void assertManifestProvider(String uri,
			Class<? extends QueryFunctionManifest.Provider> providerType) {
		QueryFunctionManifest.Entry entry = manifestEntry(uri);
		assertEquals(uri, 1, entry.mandatoryArity());
		assertTrue(uri, providerType.isInstance(entry.provider()));
	}

	private void assertDimensions(String lexicalForm, int coordinate, int spatial) throws Exception {
		assertIntegerResult(coordinate, evaluate(COORDINATE_DIMENSION_URI, wkt(lexicalForm)));
		assertIntegerResult(spatial, evaluate(SPATIAL_DIMENSION_URI, wkt(lexicalForm)));
	}

	private void assertLayout(String lexicalForm, boolean is3D, boolean isMeasured) throws Exception {
		assertBooleanResult(is3D, evaluate(IS_3D_URI, wkt(lexicalForm)));
		assertBooleanResult(isMeasured, evaluate(IS_MEASURED_URI, wkt(lexicalForm)));
	}

	private void assertEmptyMetadata(MetadataCase expected) throws Exception {
		Literal geometry = wkt(expected.lexicalForm());
		assertBooleanResult(true, evaluate(IS_EMPTY_URI, geometry));
		assertBooleanResult(true, evaluate(IS_SIMPLE_URI, geometry));
		assertIntegerResult(expected.coordinate(), evaluate(COORDINATE_DIMENSION_URI, geometry));
		assertIntegerResult(expected.spatial(), evaluate(SPATIAL_DIMENSION_URI, geometry));
		assertBooleanResult(expected.is3D(), evaluate(IS_3D_URI, geometry));
		assertBooleanResult(expected.isMeasured(), evaluate(IS_MEASURED_URI, geometry));
		Value type = evaluate(GEOMETRY_TYPE_URI, geometry);
		assertEquals(SF_NAMESPACE + expected.geometryType(), type.stringValue());
		assertEquals(XSD.ANYURI, ((Literal) type).getDatatype());
	}

	private void assertIntegerResult(int expected, Value result) {
		assertTrue(result instanceof Literal);
		assertEquals(BigInteger.valueOf(expected), ((Literal) result).integerValue());
		assertEquals(XSD.INTEGER, ((Literal) result).getDatatype());
	}

	private void assertBooleanResult(boolean expected, Value result) {
		assertEquals(VALUE_FACTORY.createLiteral(expected), result);
		assertEquals(XSD.BOOLEAN, ((Literal) result).getDatatype());
	}

	private record MetadataCase(String lexicalForm, String geometryType, int coordinate,
			int spatial, boolean is3D, boolean isMeasured) {
	}
}
