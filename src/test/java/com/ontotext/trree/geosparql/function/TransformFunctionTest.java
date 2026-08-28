package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.GeoJsonResultDimensionPolicy;
import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.GeometryWrapperFactory;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.function.Function;
import org.eclipse.rdf4j.query.algebra.evaluation.function.FunctionRegistry;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.io.WKTReader;
import org.opengis.referencing.operation.TransformException;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TransformFunctionTest {
	private static final String TRANSFORM_URI = GeoConstants.NS_GEOF + "transform";
	private static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
	private static final String EPSG_4326 = "http://www.opengis.net/def/crs/EPSG/0/4326";
	private static final String EPSG_4979 = "http://www.opengis.net/def/crs/EPSG/0/4979";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String GDA94_3D = "http://www.opengis.net/def/crs/EPSG/0/4939";
	private static final String GDA2020_3D = "http://www.opengis.net/def/crs/EPSG/0/7843";
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE =
			new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void manifestEntryDefinesExactUriMandatoryArityAndPreserveDefinedZPolicy() {
		QueryFunctionManifest.Entry entry = manifestEntry();

		assertEquals(TRANSFORM_URI, entry.uri());
		assertEquals(2, entry.mandatoryArity());
		assertTrue(entry.provider() instanceof QueryFunctionManifest.GeometryTargetSrsProvider);
		QueryFunctionManifest.GeometryTargetSrsProvider provider =
				(QueryFunctionManifest.GeometryTargetSrsProvider) entry.provider();
		assertEquals(GeoJsonResultDimensionPolicy.PRESERVE_DEFINED_Z,
				provider.geoJsonResultDimensionPolicy());
	}

	@Test
	public void transformAcceptsTargetSrsIriAndRetainsWktDatatype() throws Exception {
		Literal source = wkt("POINT(24.5887755 41.4035958)");

		Literal result = (Literal) evaluate(
				source, VALUE_FACTORY.createIRI(EPSG_32634));

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		SourceGeometryLiteral transformed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		assertEquals(EPSG_32634, transformed.effectiveCrsUri());
		Coordinate coordinate = transformed.asGeometryWrapper().getXYGeometry().getCoordinate();
		assertEquals(799997.8, coordinate.x, 0.1);
		assertEquals(4589779.63, coordinate.y, 0.1);
	}

	@Test
	public void transformAcceptsSimpleAnyUriTargetSrs() throws Exception {
		Literal projected = wkt("<" + EPSG_32634 + "> POINT(799997.8 4589779.63)");

		Literal result = (Literal) evaluate(
				projected, VALUE_FACTORY.createLiteral(CRS84, XSD.ANYURI));

		SourceGeometryLiteral transformed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		assertEquals(CRS84, transformed.effectiveCrsUri());
		Coordinate coordinate = transformed.asGeometryWrapper().getXYGeometry().getCoordinate();
		assertEquals(24.5887755, coordinate.x, 1e-6);
		assertEquals(41.4035958, coordinate.y, 1e-6);
	}

	@Test
	public void transformRejectsEmptyAnyUriTargetSrs() {
		Literal source = wkt("POINT(1 2)");

		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(source, VALUE_FACTORY.createLiteral("", XSD.ANYURI)));
	}

	@Test
	public void transformRejectsOtherTargetSrsRdfTerms() {
		Literal source = wkt("POINT(1 2)");

		for (Value target : List.of(
				VALUE_FACTORY.createLiteral(CRS84),
				VALUE_FACTORY.createLiteral(CRS84, XSD.STRING),
				VALUE_FACTORY.createLiteral(CRS84, "en"),
				VALUE_FACTORY.createLiteral(4326))) {
			assertThrows(target.toString(), ValueExprEvaluationException.class,
					() -> evaluate(source, target));
		}
	}

	@Test
	public void transformEnforcesGeometryArgumentAndMandatoryArity() {
		Literal source = wkt("POINT(1 2)");
		Value target = VALUE_FACTORY.createIRI(CRS84);

		assertThrows(ValueExprEvaluationException.class, this::evaluate);
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(source));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(source, target, target));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(VALUE_FACTORY.createIRI("http://example.com/geometry"), target));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(wkt("not geometry"), target));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(VALUE_FACTORY.createLiteral(
						"POINT(1 2)", VALUE_FACTORY.createIRI("http://example.com/datatype")), target));
	}

	@Test
	public void transformRespectsAuthorityAxisOrderAndTransformsOnlyExistingVertices() throws Exception {
		Literal source = wkt("<" + EPSG_4326 + "> LINESTRING(41 24,42 25)");

		Literal result = (Literal) evaluate(source, VALUE_FACTORY.createIRI(CRS84));

		SourceGeometryLiteral transformed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		assertEquals(2, transformed.asGeometryWrapper().getXYGeometry().getNumPoints());
		Coordinate[] coordinates = transformed.asGeometryWrapper().getXYGeometry().getCoordinates();
		assertEquals(24.0, coordinates[0].x, 0.0);
		assertEquals(41.0, coordinates[0].y, 0.0);
		assertEquals(25.0, coordinates[1].x, 0.0);
		assertEquals(42.0, coordinates[1].y, 0.0);
	}

	@Test
	public void transformRejectsUnsupportedCrsAndUnavailableCoordinateOperations() {
		Literal source = wkt("POINT(1 2)");
		Literal unsupportedSource = wkt(
				"<http://example.com/crs/unknown> POINT(1 2)");
		Literal threeDimensional = wkt(
				"<" + EPSG_4979 + "> POINT Z(-27.47 153.03 55)");

		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(unsupportedSource, VALUE_FACTORY.createIRI(CRS84)));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(source,
						VALUE_FACTORY.createIRI("http://example.com/crs/unknown")));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(threeDimensional, VALUE_FACTORY.createIRI(EPSG_32634)));
	}

	@Test
	public void transformReportsTransformationFailuresAsExpressionErrors() {
		TransformException failure = new TransformException("Coordinate transformation failed");
		QueryFunctionManifest.Entry entry = new QueryFunctionManifest.Entry(
				TRANSFORM_URI, 2,
				new QueryFunctionManifest.GeometryTargetSrsProvider(
						(geometry, targetSrsUri) -> {
							throw failure;
						},
						GeoJsonResultDimensionPolicy.PRESERVE_DEFINED_Z));
		Function function = new QueryFunctionRdf4jAdapter(entry);

		ValueExprEvaluationException exception = assertThrows(
				ValueExprEvaluationException.class,
				() -> function.evaluate(
						TRIPLE_SOURCE, wkt("POINT(1 2)"), VALUE_FACTORY.createIRI(CRS84)));

		assertEquals(failure, exception.getCause());
	}

	@Test
	public void transformRetainsEmptyGeometryTypeAndCoordinateLayout() throws Exception {
		Literal source = wkt("<" + EPSG_32634 + "> LINESTRING ZM EMPTY");

		Literal result = (Literal) evaluate(source, VALUE_FACTORY.createIRI(CRS84));

		assertEquals("LINESTRING ZM EMPTY", result.stringValue());
		SourceGeometryLiteral transformed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		assertEquals("LineString",
				transformed.asGeometryWrapper().getParsingGeometry().getGeometryType());
		assertEquals(4, transformed.asGeometryWrapper().getCoordinateDimension());
		assertEquals(3, transformed.asGeometryWrapper().getSpatialDimension());
	}

	@Test
	public void twoDimensionalOperationRetainsSourceZAndMOrdinates() throws Exception {
		Literal source = wkt(
				"<" + EPSG_32634 + "> POINT ZM(500000 4600000 120 8)");

		Literal result = (Literal) evaluate(source, VALUE_FACTORY.createIRI(CRS84));

		SourceGeometryLiteral transformed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		Coordinate coordinate = transformed.asGeometryWrapper().getParsingGeometry().getCoordinate();
		assertEquals(120.0, coordinate.getZ(), 0.0);
		assertEquals(8.0, coordinate.getM(), 0.0);
		assertEquals(4, transformed.asGeometryWrapper().getCoordinateDimension());
		assertEquals(3, transformed.asGeometryWrapper().getSpatialDimension());
	}

	@Test
	public void threeDimensionalOperationTransformsZ() throws Exception {
		Literal source = wkt(
				"<" + GDA94_3D + "> POINT Z(-27.47 153.03 3000)");

		Literal result = (Literal) evaluate(source, VALUE_FACTORY.createIRI(GDA2020_3D));

		SourceGeometryLiteral transformed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		assertEquals(GDA2020_3D, transformed.effectiveCrsUri());
		Coordinate coordinate = transformed.asGeometryWrapper().getParsingGeometry().getCoordinate();
		assertEquals(-27.469987, coordinate.x, 1e-6);
		assertEquals(153.030006, coordinate.y, 1e-6);
		assertEquals(2999.897775, coordinate.getZ(), 1e-6);
		assertEquals(3, transformed.asGeometryWrapper().getCoordinateDimension());
		assertEquals(3, transformed.asGeometryWrapper().getSpatialDimension());
	}

	@Test
	public void transformRetainsGmlDatatypeAndUsesRequestedTargetSrs() throws Exception {
		Literal source = gmlFromWkt("POINT(24.5887755 41.4035958)");

		Literal result = (Literal) evaluate(source, VALUE_FACTORY.createIRI(EPSG_32634));

		assertEquals(GeoConstants.GEO_GML_LITERAL, result.getDatatype());
		SourceGeometryLiteral transformed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		assertEquals(EPSG_32634, transformed.effectiveCrsUri());
		Coordinate coordinate = transformed.asGeometryWrapper().getXYGeometry().getCoordinate();
		assertEquals(799997.8, coordinate.x, 0.1);
		assertEquals(4589779.63, coordinate.y, 0.1);
	}

	@Test
	public void geoJsonIdentityTransformRetainsDefinedZAndCanonicalizesEmptyResultsToXy() throws Exception {
		Literal xy = geoJson("{\"type\":\"Point\",\"coordinates\":[1,2]}");
		Literal xyz = geoJson("{\"type\":\"Point\",\"coordinates\":[1,2,3]}");
		Literal empty = geoJson("{\"type\":\"LineString\",\"coordinates\":[]}");

		assertGeoJsonLayout((Literal) evaluate(xy, VALUE_FACTORY.createIRI(CRS84)), 2, Double.NaN);
		assertGeoJsonLayout((Literal) evaluate(xyz, VALUE_FACTORY.createIRI(CRS84)), 3, 3.0);
		Literal emptyResult = (Literal) evaluate(empty, VALUE_FACTORY.createIRI(CRS84));
		assertGeoJsonLayout(emptyResult, 2, Double.NaN);
		assertTrue(JenaGeometryAdapter.toSourceGeometryLiteral(emptyResult)
				.asGeometryWrapper().isEmpty());
	}

	@Test
	public void geoJsonTransformRejectsNonCrs84TargetsAndInvalidAltitudeLayouts() {
		Literal xyz = geoJson("{\"type\":\"Point\",\"coordinates\":[1,2,3]}");
		Literal nonFiniteZ = geoJson("{\"type\":\"Point\",\"coordinates\":[1,2,null]}");
		Literal mixedZ = geoJson("{\"type\":\"LineString\",\"coordinates\":[[0,0,1],[1,1]]}");

		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(xyz, VALUE_FACTORY.createIRI(EPSG_32634)));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(nonFiniteZ, VALUE_FACTORY.createIRI(CRS84)));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(mixedZ, VALUE_FACTORY.createIRI(CRS84)));
	}

	@Test
	public void geoJsonTransformRejectsLostOrNonFiniteRequiredSourceAltitude() {
		Function lostAltitude = transformAdapterReturning(new Coordinate(1, 2));
		Function nonFiniteAltitude = transformAdapterReturning(
				new Coordinate(1, 2, Double.POSITIVE_INFINITY));
		Literal xyz = geoJson("{\"type\":\"Point\",\"coordinates\":[1,2,3]}");

		assertThrows(ValueExprEvaluationException.class,
				() -> lostAltitude.evaluate(
						TRIPLE_SOURCE, xyz, VALUE_FACTORY.createIRI(CRS84)));
		assertThrows(ValueExprEvaluationException.class,
				() -> nonFiniteAltitude.evaluate(
						TRIPLE_SOURCE, xyz, VALUE_FACTORY.createIRI(CRS84)));
	}

	@Test
	public void transformResultBoundaryReturnsFiniteResultsOutsideTheTargetCrsDomainOfValidity()
			throws Exception {
		Function function = transformAdapterReturning(new Coordinate(181, 0));

		Literal result = (Literal) function.evaluate(
				TRIPLE_SOURCE, wkt("POINT(1 2)"), VALUE_FACTORY.createIRI(CRS84));

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		Coordinate coordinate = new WKTReader().read(result.stringValue()).getCoordinate();
		assertTrue(Double.isFinite(coordinate.x));
		assertTrue(Double.isFinite(coordinate.y));
		assertEquals(181.0, coordinate.x, 0.0);
		assertEquals(0.0, coordinate.y, 0.0);
	}

	@Test
	public void transformResultBoundaryRejectsNonFiniteCoordinates() {
		Function function = transformAdapterReturning(
				new Coordinate(Double.POSITIVE_INFINITY, 0));

		for (Literal source : List.of(
				wkt("POINT(1 2)"),
				gmlFromWkt("POINT(1 2)"),
				geoJson("{\"type\":\"Point\",\"coordinates\":[1,2]}"))) {
			assertThrows(source.getDatatype().stringValue(), ValueExprEvaluationException.class,
					() -> function.evaluate(
							TRIPLE_SOURCE, source, VALUE_FACTORY.createIRI(CRS84)));
		}
	}

	@Test
	public void transformResultBoundaryRejectsUnexpectedTargetSrs() {
		QueryFunctionManifest.Entry entry = new QueryFunctionManifest.Entry(
				TRANSFORM_URI, 2,
				new QueryFunctionManifest.GeometryTargetSrsProvider(
						(geometry, targetSrsUri) -> GeometryWrapperFactory.createPoint(
								new Coordinate(1, 2), CRS84,
								geometry.getGeometryDatatypeURI()),
						GeoJsonResultDimensionPolicy.PRESERVE_DEFINED_Z));
		Function function = new QueryFunctionRdf4jAdapter(entry);

		assertThrows(ValueExprEvaluationException.class,
				() -> function.evaluate(
						TRIPLE_SOURCE, wkt("POINT(1 2)"),
						VALUE_FACTORY.createIRI(EPSG_32634)));
	}

	@Test
	public void transformReturnsComputableProjectedResultsOutsideTheTargetCrsDomainOfValidity()
			throws Exception {
		Literal result = (Literal) evaluate(
				wkt("POINT(0 0)"), VALUE_FACTORY.createIRI(EPSG_32634));

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		SourceGeometryLiteral transformed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		assertEquals(EPSG_32634, transformed.effectiveCrsUri());
		Coordinate coordinate = transformed.asGeometryWrapper().getXYGeometry().getCoordinate();
		assertTrue(Double.isFinite(coordinate.x));
		assertTrue(Double.isFinite(coordinate.y));
		assertEquals(-1891310.54, coordinate.x, 0.1);
		assertEquals(0.0, coordinate.y, 1e-6);
	}

	@Test
	public void transformSupportsGenericGeometryCollections() throws Exception {
		Literal source = wkt("<" + EPSG_4326 + "> GEOMETRYCOLLECTION("
				+ "POINT(41 24),LINESTRING(41 24,42 25))");

		Literal result = (Literal) evaluate(source, VALUE_FACTORY.createIRI(CRS84));

		SourceGeometryLiteral transformed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		assertEquals("GeometryCollection",
				transformed.asGeometryWrapper().getXYGeometry().getGeometryType());
		assertEquals(2, transformed.asGeometryWrapper().getXYGeometry().getNumGeometries());
		assertEquals(2, transformed.asGeometryWrapper().getXYGeometry()
				.getGeometryN(1).getNumPoints());
	}

	private QueryFunctionManifest.Entry manifestEntry() {
		return QueryFunctionManifest.entries().stream()
				.filter(entry -> TRANSFORM_URI.equals(entry.uri()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing manifest entry: " + TRANSFORM_URI));
	}

	private Value evaluate(Value... args) throws ValueExprEvaluationException {
		GeoSparqlFunctionRegistration.registerAll();
		Function function = FunctionRegistry.getInstance().get(TRANSFORM_URI)
				.orElseThrow(() -> new AssertionError("Function not registered: " + TRANSFORM_URI));
		return function.evaluate(TRIPLE_SOURCE, args);
	}

	private Literal wkt(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL);
	}

	private Literal gmlFromWkt(String lexicalForm) {
		SourceGeometryLiteral source = JenaGeometryAdapter.toSourceGeometryLiteral(wkt(lexicalForm));
		return JenaGeometryAdapter.toRdf4jLiteral(VALUE_FACTORY,
				source.asGeometryWrapper(), GeoConstants.GEO_GML_LITERAL);
	}

	private Literal geoJson(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_JSON_LITERAL);
	}

	private Function transformAdapterReturning(Coordinate coordinate) {
		QueryFunctionManifest.Entry entry = new QueryFunctionManifest.Entry(
				TRANSFORM_URI, 2,
				new QueryFunctionManifest.GeometryTargetSrsProvider(
						(geometry, targetSrsUri) -> GeometryWrapperFactory.createPoint(
								coordinate, targetSrsUri, geometry.getGeometryDatatypeURI()),
						GeoJsonResultDimensionPolicy.PRESERVE_DEFINED_Z));
		return new QueryFunctionRdf4jAdapter(entry);
	}

	private void assertGeoJsonLayout(Literal result, int coordinateDimension, double z) {
		assertEquals(GeoConstants.GEO_JSON_LITERAL, result.getDatatype());
		SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		assertEquals(CRS84, parsed.effectiveCrsUri());
		assertEquals(coordinateDimension,
				parsed.asGeometryWrapper().getCoordinateDimension());
		Coordinate coordinate = parsed.asGeometryWrapper().getParsingGeometry().getCoordinate();
		if (Double.isNaN(z)) {
			if (coordinate != null) {
				assertTrue(Double.isNaN(coordinate.getZ()));
			}
		} else {
			assertEquals(z, coordinate.getZ(), 0.0);
		}
	}
}
