package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.GeoJsonResultDimensionPolicy;
import com.ontotext.trree.geosparql.jena.GeoSparqlUnits;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QueryFunctionRdf4jAdapterTest {
	private static final ValueFactory VF = SimpleValueFactory.getInstance();
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";

	@Test
	public void scalarProviderReceivesNativeUnitAndItsResultIsScaledAtTheBoundary() throws Exception {
		QueryFunctionRdf4jAdapter function = new QueryFunctionRdf4jAdapter(new QueryFunctionManifest.Entry(
				GeoConstants.GEOF_LENGTH.stringValue(), 2,
				new QueryFunctionManifest.UnaryGeometryUnitToDoubleProvider((geometry, unit) -> {
					assertEquals(Unit_URI.METRE_URL, unit);
					return geometry.getXYGeometry().getLength();
				})));
		Literal line = wkt("<" + EPSG_32634 + "> LINESTRING(500000 4600000,500003 4600004)");

		Literal result = (Literal) function.evaluate(VF, line, GeoSparqlUnits.URI_LIGHT_YEAR);

		assertEquals(5.285117052613511e-16, result.doubleValue(), 1e-30);
	}

	@Test
	public void bufferProviderReceivesNativeUnitAndRadiusScaledAtTheBoundary() throws Exception {
		QueryFunctionRdf4jAdapter function = new QueryFunctionRdf4jAdapter(new QueryFunctionManifest.Entry(
				GeoConstants.GEOF_BUFFER.stringValue(), 3,
				new QueryFunctionManifest.UnaryGeometryDoubleUnitToGeometryProvider((geometry, radius, unit) -> {
					assertEquals(Unit_URI.METRE_URL, unit);
					assertEquals(1.0, radius, 1e-15);
					return geometry.buffer(radius, Unit_URI.METRE_URL);
				}, GeoJsonResultDimensionPolicy.XY_ONLY)));
		Literal point = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");

		Literal result = (Literal) function.evaluate(VF, point,
				VF.createLiteral(1.0 / 9.460528405e15), GeoSparqlUnits.URI_LIGHT_YEAR);

		assertEquals(500001.0, SourceGeometryLiteral.fromLiteral(result)
				.asGeometryWrapper().getXYGeometry().getEnvelopeInternal().getMaxX(), 1e-7);
	}

	@Test
	public void squaredOutputScaleIsIndependentOfTheCalculationImplementation() throws Exception {
		QueryFunctionRdf4jAdapter function = new QueryFunctionRdf4jAdapter(new QueryFunctionManifest.Entry(
				GeoConstants.GEOF_AREA.stringValue(), 2,
				new QueryFunctionManifest.UnaryGeometryUnitToDoubleProvider((geometry, unit) -> {
					assertEquals(Unit_URI.METRE_URL, unit);
					return geometry.getXYGeometry().getArea();
				}, 2)));
		Literal polygon = wkt("<" + EPSG_32634 + "> POLYGON(("
				+ "500000 4600000,501000 4600000,501000 4601000,"
				+ "500000 4601000,500000 4600000))");

		Literal result = (Literal) function.evaluate(VF, polygon, GeoSparqlUnits.URI_LIGHT_YEAR);

		assertEquals(1.1172984903930451e-26, result.doubleValue(), 1e-39);
	}

	@Test
	public void nativeJenaDistanceProviderRetainsUnitCompatibility() throws Exception {
		QueryFunctionRdf4jAdapter function = new QueryFunctionRdf4jAdapter(new QueryFunctionManifest.Entry(
				GeoConstants.GEOF_DISTANCE.stringValue(), 3,
				new QueryFunctionManifest.BinaryGeometryUnitToDoubleProvider(GeometryWrapper::distance)));
		Literal left = wkt("<" + EPSG_32634 + "> POINT(500000 4600000)");
		Literal right = wkt("<" + EPSG_32634 + "> POINT(500003 4600004)");

		Literal result = (Literal) function.evaluate(VF, left, right, GeoSparqlUnits.URI_LIGHT_YEAR);

		assertEquals(5.285117052613511e-16, result.doubleValue(), 1e-30);
	}

	private Literal wkt(String value) {
		return VF.createLiteral(value, GeoConstants.GEO_WKT_LITERAL);
	}
}
