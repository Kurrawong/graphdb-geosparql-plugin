package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.GeoSparqlUnits;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GeoSparqlFunctionRegistrationTest {
    private static final String GEOF_DIMENSION_URI = GeoConstants.NS_GEOF + "dimension";
    private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
    private static final ValueFactoryTripleSource TRIPLE_SOURCE = new ValueFactoryTripleSource(VALUE_FACTORY);

    @Test
    public void registerAllRegistersSupportedFunctionUris() {
        GeoSparqlFunctionRegistration.registerAll();

        FunctionRegistry registry = FunctionRegistry.getInstance();
        for (String uri : GeoSparqlFunctionRegistration.supportedFunctionUris()) {
            assertTrue("Expected registered function URI: " + uri, registry.has(uri));
            assertEquals(uri, registry.get(uri).get().getURI());
        }
    }

    @Test
    public void registeredDistanceFunctionDelegatesToJenaEvaluator() throws Exception {
        GeoSparqlFunctionRegistration.registerAll();
        Function function = FunctionRegistry.getInstance().get(GeoConstants.GEOF_DISTANCE.stringValue()).get();
        Literal left = VALUE_FACTORY.createLiteral("POINT(24.5887755 41.4035958)",
                GeoConstants.GEO_WKT_LITERAL);
        Literal right = VALUE_FACTORY.createLiteral(
                "<http://www.opengis.net/def/crs/EPSG/0/32634> POINT(799997.80 4589779.63)",
                GeoConstants.GEO_WKT_LITERAL);

		Value result = function.evaluate(TRIPLE_SOURCE, left, right, GeoSparqlUnits.URI_METRE);

        assertTrue(result instanceof Literal);
        assertEquals(0d, ((Literal) result).doubleValue(), 0.2d);
        assertTrue(function instanceof GeoSparqlRdf4jFunction);
    }

    @Test
    public void registeredGeofDimensionReturnsTopologicalDimensionAsInteger() throws Exception {
        GeoSparqlFunctionRegistration.registerAll();
        Function function = FunctionRegistry.getInstance().get(GEOF_DIMENSION_URI).get();
        Literal polygon = VALUE_FACTORY.createLiteral(
                "POLYGON((0 0,0 2,2 2,2 0,0 0))", GeoConstants.GEO_WKT_LITERAL);

        Value result = function.evaluate(TRIPLE_SOURCE, polygon);

        assertTrue(result instanceof Literal);
        assertEquals(BigInteger.valueOf(2), ((Literal) result).integerValue());
        assertEquals(XSD.INTEGER, ((Literal) result).getDatatype());
    }

    @Test
    public void geofDimensionManifestEntryDefinesMandatoryArityAndTypedProvider() {
        List<Clause109QueryFunctionManifest.Entry> entries = Clause109QueryFunctionManifest.entries().stream()
                .filter(entry -> GEOF_DIMENSION_URI.equals(entry.uri()))
                .toList();

        assertEquals(1, entries.size());
        assertEquals(1, entries.getFirst().mandatoryArity());
        assertTrue(entries.getFirst().provider()
                instanceof Clause109QueryFunctionManifest.UnaryGeometryIntegerProvider);
    }

    @Test
    public void registeredGeofDimensionEnforcesMandatoryArity() {
        Function function = registeredDimensionFunction();
        Literal point = wkt("POINT(1 2)");

        assertThrows(ValueExprEvaluationException.class,
                () -> function.evaluate(TRIPLE_SOURCE));
        assertThrows(ValueExprEvaluationException.class,
                () -> function.evaluate(TRIPLE_SOURCE, point, point));
    }

    @Test
    public void registeredGeofDimensionRejectsNonLiteralRdfTerm() {
        Function function = registeredDimensionFunction();

        assertThrows(ValueExprEvaluationException.class,
                () -> function.evaluate(TRIPLE_SOURCE,
                        VALUE_FACTORY.createIRI("http://example.com/geometry")));
    }

    @Test
    public void registeredGeofDimensionRejectsMalformedGeometryLiteral() {
        Function function = registeredDimensionFunction();

        assertThrows(ValueExprEvaluationException.class,
                () -> function.evaluate(TRIPLE_SOURCE, wkt("not geometry")));
    }

    @Test
    public void registeredGeofDimensionUsesMaximumDimensionForHeterogeneousCollection() throws Exception {
        assertDimension(2, "GEOMETRYCOLLECTION(POINT(1 1),LINESTRING(0 0,1 1),"
                + "POLYGON((0 0,0 2,2 2,2 0,0 0)))");
    }

    @Test
    public void registeredGeofDimensionRetainsStructuralEmptyDimensions() throws Exception {
        assertDimension(0, "POINT EMPTY");
        assertDimension(0, "MULTIPOINT EMPTY");
        assertDimension(1, "LINESTRING EMPTY");
        assertDimension(1, "MULTILINESTRING EMPTY");
        assertDimension(2, "POLYGON EMPTY");
        assertDimension(2, "MULTIPOLYGON EMPTY");
        assertDimension(-1, "GEOMETRYCOLLECTION EMPTY");
        assertDimension(2, VALUE_FACTORY.createLiteral(
                "{\"type\":\"GeometryCollection\",\"geometries\":["
                        + "{\"type\":\"Point\",\"coordinates\":[]},"
                        + "{\"type\":\"LineString\",\"coordinates\":[]},"
                        + "{\"type\":\"Polygon\",\"coordinates\":[]}]}",
                GeoConstants.GEO_JSON_LITERAL));
    }

    @Test
    public void legacyGeoDimensionAliasRemainsRegistered() throws Exception {
        GeoSparqlFunctionRegistration.registerAll();
        Function function = FunctionRegistry.getInstance().get(GeoConstants.GEO_DIMENSION.stringValue()).get();

        Value result = function.evaluate(TRIPLE_SOURCE, wkt("LINESTRING(0 0,1 1)"));

        assertEquals(BigInteger.ONE, ((Literal) result).integerValue());
        assertTrue(function instanceof GeoSparqlRdf4jFunction);
    }

    @Test
    public void registerAllIsIdempotent() {
        GeoSparqlFunctionRegistration.registerAll();
        GeoSparqlFunctionRegistration.registerAll();

        Set<String> supportedUris = GeoSparqlFunctionRegistration.supportedFunctionUris().stream()
                .collect(Collectors.toSet());
        long registeredSupportedUris = FunctionRegistry.getInstance().getKeys().stream()
                .filter(supportedUris::contains)
                .count();

        assertEquals(supportedUris.size(), registeredSupportedUris);
    }

    private Function registeredDimensionFunction() {
        GeoSparqlFunctionRegistration.registerAll();
        return FunctionRegistry.getInstance().get(GEOF_DIMENSION_URI).get();
    }

    private Literal wkt(String lexicalForm) {
        return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL);
    }

    private void assertDimension(int expected, String lexicalForm) throws Exception {
        assertDimension(expected, wkt(lexicalForm));
    }

    private void assertDimension(int expected, Literal geometry) throws Exception {
        Value result = registeredDimensionFunction().evaluate(TRIPLE_SOURCE, geometry);

        assertTrue(result instanceof Literal);
        assertEquals(BigInteger.valueOf(expected), ((Literal) result).integerValue());
        assertEquals(XSD.INTEGER, ((Literal) result).getDatatype());
    }
}
