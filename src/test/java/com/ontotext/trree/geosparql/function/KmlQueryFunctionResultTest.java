package com.ontotext.trree.geosparql.function;

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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Verifies that geometry-returning query functions keep KML source literals as KML results. */
public class KmlQueryFunctionResultTest {
	private static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE = new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void geometryResultOfKmlSourceIsKmlLiteral() throws Exception {
		Literal source = kml("<MultiGeometry><Point><coordinates>0,0</coordinates></Point>"
				+ "<Point><coordinates>2,0</coordinates></Point>"
				+ "<Point><coordinates>0,2</coordinates></Point></MultiGeometry>");

		Literal result = (Literal) evaluate(GeoConstants.GEOF_CONVEX_HULL.stringValue(), source);

		assertEquals(GeoConstants.GEO_KML_LITERAL, result.getDatatype());
		SourceGeometryLiteral parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		assertEquals(CRS84, parsed.effectiveCrsUri());
		assertEquals("Polygon", parsed.asGeometryWrapper().getParsingGeometry().getGeometryType());
		assertEquals(2.0, parsed.asGeometryWrapper().getParsingGeometry().getArea(), 0.0);
	}

	@Test
	public void transformOfKmlSourceToAnotherSrsIsExpressionError() {
		Literal source = kml("<Point><coordinates>1,2</coordinates></Point>");

		ValueExprEvaluationException exception = assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(GeoConstants.GEOF_TRANSFORM.stringValue(), source,
						VALUE_FACTORY.createLiteral("http://www.opengis.net/def/crs/EPSG/0/3857", XSD.ANYURI)));

		assertTrue(exception.getMessage(), exception.getMessage().contains("KML output requires CRS84"));
	}

	@Test
	public void altitudePreservingOperationKeepsKmlAltitude() throws Exception {
		Literal source = kml("<Point><coordinates>100,10,5</coordinates></Point>");

		Literal result = (Literal) evaluate(GeoConstants.NS_GEOF + "transformCRS84", source);

		assertEquals(GeoConstants.GEO_KML_LITERAL, result.getDatatype());
		assertEquals(5.0, JenaGeometryAdapter.toSourceGeometryLiteral(result)
				.asGeometryWrapper().getParsingGeometry().getCoordinate().getZ(), 0.0);
	}

	@Test
	public void boundaryWithSeveralRingsReadsBackAsEquivalentLines() throws Exception {
		Literal source = kml("<Polygon><outerBoundaryIs><LinearRing><coordinates>0,0 10,0 10,10 0,10 0,0"
				+ "</coordinates></LinearRing></outerBoundaryIs><innerBoundaryIs><LinearRing><coordinates>"
				+ "2,2 2,4 4,4 4,2 2,2</coordinates></LinearRing></innerBoundaryIs></Polygon>");

		Literal result = (Literal) evaluate(GeoConstants.GEOF_BOUNDARY.stringValue(), source);

		Geometry parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result).asGeometryWrapper().getXYGeometry();
		assertEquals(2, parsed.getNumGeometries());
		assertTrue(parsed.toText(), new WKTReader().read(
				"MULTILINESTRING((0 0,10 0,10 10,0 10,0 0),(2 2,2 4,4 4,4 2,2 2))").equalsTopo(parsed));
	}

	private static Value evaluate(String functionUri, Value... args) throws ValueExprEvaluationException {
		GeoSparqlFunctionRegistration.registerAll();
		Function function = FunctionRegistry.getInstance().get(functionUri)
				.orElseThrow(() -> new AssertionError("Function not registered: " + functionUri));
		return function.evaluate(TRIPLE_SOURCE, args);
	}

	private static Literal kml(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_KML_LITERAL);
	}
}
