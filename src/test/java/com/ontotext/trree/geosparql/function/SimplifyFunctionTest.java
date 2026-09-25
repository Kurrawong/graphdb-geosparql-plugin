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
import org.locationtech.jts.geom.LineString;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

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
