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

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class QueryFunctionProfileTest {
	private static final String GEOF = "http://www.opengis.net/def/function/geosparql/";
	private static final Set<String> REQUIREMENT_39_URIS = Set.of(
			GEOF + "boundary",
			GEOF + "boundingCircle",
			GEOF + "metricBuffer",
			GEOF + "buffer",
			GEOF + "centroid",
			GEOF + "convexHull",
			GEOF + "concaveHull",
			GEOF + "coordinateDimension",
			GEOF + "difference",
			GEOF + "dimension",
			GEOF + "metricDistance",
			GEOF + "distance",
			GEOF + "envelope",
			GEOF + "geometryType",
			GEOF + "intersection",
			GEOF + "is3D",
			GEOF + "isEmpty",
			GEOF + "isMeasured",
			GEOF + "isSimple",
			GEOF + "spatialDimension",
			GEOF + "symDifference",
			GEOF + "transform",
			GEOF + "union");
	private static final Set<String> REQUIREMENT_40_URIS = Set.of(
			GEOF + "metricArea",
			GEOF + "area",
			GEOF + "geometryN",
			GEOF + "metricLength",
			GEOF + "length",
			GEOF + "maxX",
			GEOF + "maxY",
			GEOF + "maxZ",
			GEOF + "minX",
			GEOF + "minY",
			GEOF + "minZ",
			GEOF + "numGeometries",
			GEOF + "perimeter",
			GEOF + "metricPerimeter");
	private static final Set<String> EMPTY_ERROR_URIS = Set.of(
			GeoConstants.GEOF_MAX_X.stringValue(),
			GeoConstants.GEOF_MAX_Y.stringValue(),
			GeoConstants.GEOF_MAX_Z.stringValue(),
			GeoConstants.GEOF_MIN_X.stringValue(),
			GeoConstants.GEOF_MIN_Y.stringValue(),
			GeoConstants.GEOF_MIN_Z.stringValue());
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String METRE = "http://www.opengis.net/def/uom/OGC/1.0/metre";
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE =
			new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void manifestContainsTheExactRequirement39And40Profiles() {
		Map<QueryFunctionManifest.Requirement, Set<String>> actual =
				new EnumMap<>(QueryFunctionManifest.Requirement.class);
		for (QueryFunctionManifest.Entry entry : QueryFunctionManifest.entries()) {
			actual.computeIfAbsent(entry.requirement(), ignored -> new HashSet<>()).add(entry.uri());
		}

		assertEquals(REQUIREMENT_39_URIS, actual.get(QueryFunctionManifest.Requirement.R39));
		assertEquals(REQUIREMENT_40_URIS, actual.get(QueryFunctionManifest.Requirement.R40));
		assertEquals(37, QueryFunctionManifest.entries().size());
		assertEquals(37, actual.values().stream().mapToInt(Set::size).sum());
	}

	@Test
	public void everyProfileFunctionRegistersOnceAndUsesItsManifestContract() throws Exception {
		GeoSparqlFunctionRegistration.registerAll();
		GeoSparqlFunctionRegistration.registerAll();

		Set<String> profileUris = QueryFunctionManifest.entries().stream()
				.map(QueryFunctionManifest.Entry::uri)
				.collect(Collectors.toSet());
		long registeredProfileUris = FunctionRegistry.getInstance().getKeys().stream()
				.filter(profileUris::contains)
				.count();
		assertEquals(37, registeredProfileUris);

		for (QueryFunctionManifest.Entry entry : QueryFunctionManifest.entries()) {
			Function function = registeredFunction(entry);
			assertTrue(entry.uri(), function instanceof QueryFunctionRdf4jAdapter);

			Value[] validArguments = validArguments(entry);
			assertEquals(entry.uri(), entry.mandatoryArity(), validArguments.length);
			assertResultMatchesProvider(entry, function.evaluate(TRIPLE_SOURCE, validArguments));

			assertThrows(entry.uri(), ValueExprEvaluationException.class,
					() -> function.evaluate(TRIPLE_SOURCE));
			Value[] extraArgument = Arrays.copyOf(validArguments, validArguments.length + 1);
			extraArgument[extraArgument.length - 1] = VALUE_FACTORY.createLiteral(true);
			assertThrows(entry.uri(), ValueExprEvaluationException.class,
					() -> function.evaluate(TRIPLE_SOURCE, extraArgument));
		}
	}

	@Test
	public void everyProfileFunctionRejectsInvalidRdfAndGeometryInputs() {
		GeoSparqlFunctionRegistration.registerAll();

		for (QueryFunctionManifest.Entry entry : QueryFunctionManifest.entries()) {
			Function function = registeredFunction(entry);
			Value[] nonLiteralGeometry = validArguments(entry);
			nonLiteralGeometry[0] = VALUE_FACTORY.createIRI("http://example.com/geometry");
			assertThrows(entry.uri(), ValueExprEvaluationException.class,
					() -> function.evaluate(TRIPLE_SOURCE, nonLiteralGeometry));

			Value[] malformedGeometry = validArguments(entry);
			malformedGeometry[0] = wkt("not geometry");
			assertThrows(entry.uri(), ValueExprEvaluationException.class,
					() -> function.evaluate(TRIPLE_SOURCE, malformedGeometry));

			Value[] unsupportedCrs = validArguments(entry);
			unsupportedCrs[0] = wkt("<http://example.com/crs/unknown> POINT(1 2)");
			assertThrows(entry.uri(), ValueExprEvaluationException.class,
					() -> function.evaluate(TRIPLE_SOURCE, unsupportedCrs));
		}
	}

	@Test
	public void everyProfileFunctionHasDocumentedEmptyInputBehavior() throws Exception {
		GeoSparqlFunctionRegistration.registerAll();

		for (QueryFunctionManifest.Entry entry : QueryFunctionManifest.entries()) {
			Function function = registeredFunction(entry);
			Value[] emptyArguments = validArguments(entry);
			emptyArguments[0] = wkt("<" + EPSG_32634 + "> POLYGON EMPTY");
			if (EMPTY_ERROR_URIS.contains(entry.uri())) {
				assertThrows(entry.uri(), ValueExprEvaluationException.class,
						() -> function.evaluate(TRIPLE_SOURCE, emptyArguments));
			} else {
				assertResultMatchesProvider(entry,
						function.evaluate(TRIPLE_SOURCE, emptyArguments));
			}
		}
	}

	private Function registeredFunction(QueryFunctionManifest.Entry entry) {
		return FunctionRegistry.getInstance().get(entry.uri())
				.orElseThrow(() -> new AssertionError("Function not registered: " + entry.uri()));
	}

	private Value[] validArguments(QueryFunctionManifest.Entry entry) {
		Literal geometry = coordinateExtremaZ(entry.uri())
				? wkt("<" + EPSG_32634 + "> POLYGON Z (("
						+ "500000 4600000 10,500010 4600000 20,500010 4600010 30,"
						+ "500000 4600010 40,500000 4600000 10))")
				: wkt("<" + EPSG_32634 + "> POLYGON (("
						+ "500000 4600000,500010 4600000,500010 4600010,"
						+ "500000 4600010,500000 4600000))");
		Literal otherGeometry = wkt("<" + EPSG_32634 + "> POLYGON (("
				+ "500005 4600005,500015 4600005,500015 4600015,"
				+ "500005 4600015,500005 4600005))");
		Value unit = VALUE_FACTORY.createIRI(METRE);
		return switch (entry.provider()) {
			case QueryFunctionManifest.BinaryGeometryProvider ignored ->
					new Value[]{geometry, otherGeometry};
			case QueryFunctionManifest.BinaryGeometryToDoubleProvider ignored ->
					new Value[]{geometry, otherGeometry};
			case QueryFunctionManifest.BinaryGeometryUnitToDoubleProvider ignored ->
					new Value[]{geometry, otherGeometry, unit};
			case QueryFunctionManifest.GeometryMemberProvider ignored ->
					new Value[]{geometry, VALUE_FACTORY.createLiteral(1)};
			case QueryFunctionManifest.GeometryTargetSrsProvider ignored ->
					new Value[]{geometry, VALUE_FACTORY.createIRI(EPSG_32634)};
			case QueryFunctionManifest.UnaryGeometryDoubleToGeometryProvider ignored ->
					new Value[]{geometry, VALUE_FACTORY.createLiteral(1)};
			case QueryFunctionManifest.UnaryGeometryDoubleUnitToGeometryProvider ignored ->
					new Value[]{geometry, VALUE_FACTORY.createLiteral(1), unit};
			case QueryFunctionManifest.UnaryGeometryUnitToDoubleProvider ignored ->
					new Value[]{geometry, unit};
			default -> new Value[]{geometry};
		};
	}

	private boolean coordinateExtremaZ(String uri) {
		return GeoConstants.GEOF_MAX_Z.stringValue().equals(uri)
				|| GeoConstants.GEOF_MIN_Z.stringValue().equals(uri);
	}

	private void assertResultMatchesProvider(QueryFunctionManifest.Entry entry, Value result) {
		assertTrue(entry.uri(), result instanceof Literal);
		Literal literal = (Literal) result;
		QueryFunctionManifest.Provider provider = entry.provider();
		if (provider instanceof QueryFunctionManifest.BinaryGeometryProvider
				|| provider instanceof QueryFunctionManifest.GeometryMemberProvider
				|| provider instanceof QueryFunctionManifest.GeometryTargetSrsProvider
				|| provider instanceof QueryFunctionManifest.UnaryGeometryDoubleToGeometryProvider
				|| provider instanceof QueryFunctionManifest.UnaryGeometryDoubleUnitToGeometryProvider
				|| provider instanceof QueryFunctionManifest.UnaryGeometryProvider) {
			assertEquals(entry.uri(), GeoConstants.GEO_WKT_LITERAL, literal.getDatatype());
		} else if (provider instanceof QueryFunctionManifest.UnaryGeometryAnyUriProvider) {
			assertEquals(entry.uri(), XSD.ANYURI, literal.getDatatype());
		} else if (provider instanceof QueryFunctionManifest.UnaryGeometryBooleanProvider) {
			assertEquals(entry.uri(), XSD.BOOLEAN, literal.getDatatype());
		} else if (provider instanceof QueryFunctionManifest.UnaryGeometryIntegerProvider) {
			assertEquals(entry.uri(), XSD.INTEGER, literal.getDatatype());
		} else if (provider instanceof QueryFunctionManifest.BinaryGeometryToDoubleProvider
				|| provider instanceof QueryFunctionManifest.BinaryGeometryUnitToDoubleProvider
				|| provider instanceof QueryFunctionManifest.UnaryGeometryToDoubleProvider
				|| provider instanceof QueryFunctionManifest.UnaryGeometryUnitToDoubleProvider) {
			assertEquals(entry.uri(), XSD.DOUBLE, literal.getDatatype());
		} else {
			fail("Unverified provider for " + entry.uri() + ": " + provider.getClass().getName());
		}
	}

	private Literal wkt(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL);
	}
}
