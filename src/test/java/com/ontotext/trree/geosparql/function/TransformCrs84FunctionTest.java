package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.function.Function;
import org.eclipse.rdf4j.query.algebra.evaluation.function.FunctionRegistry;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TransformCrs84FunctionTest {
	private static final String URI = GeoConstants.NS_GEOF + "transformCRS84";
	private static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE =
			new ValueFactoryTripleSource(VALUE_FACTORY);

	@Test
	public void authorityAxisPointTransformsToCrs84AndRetainsWktDatatype() throws Exception {
		Literal source = wkt("<http://www.opengis.net/def/crs/EPSG/0/4326> POINT(10 100)");

		Literal result = (Literal) evaluate(source);
		SourceGeometryLiteral transformed = JenaGeometryAdapter.toSourceGeometryLiteral(result);
		Coordinate coordinate = transformed.asGeometryWrapper().getParsingGeometry().getCoordinate();

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertEquals(CRS84, transformed.effectiveCrsUri());
		assertEquals(100.0, coordinate.x, 0.0);
		assertEquals(10.0, coordinate.y, 0.0);
	}

	@Test
	public void invalidGeometryAndArityProduceExpressionErrors() {
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(wkt("<http://example.com/crs/unknown> POINT(1 2)")));
		assertThrows(ValueExprEvaluationException.class, () -> evaluate());
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(wkt("POINT(1 2)"), wkt("POINT(3 4)")));
	}

	@Test
	public void gmlAndGeoJsonResultsRetainTheirLiteralDatatypes() throws Exception {
		Literal wktSource = wkt("<http://www.opengis.net/def/crs/EPSG/0/4326> POINT(10 100)");
		Literal gmlSource = JenaGeometryAdapter.toRdf4jLiteral(VALUE_FACTORY,
				JenaGeometryAdapter.toSourceGeometryLiteral(wktSource).asGeometryWrapper(),
				GeoConstants.GEO_GML_LITERAL);
		Literal geoJsonSource = VALUE_FACTORY.createLiteral(
				"{\"type\":\"Point\",\"coordinates\":[100,10,5]}",
				GeoConstants.GEO_JSON_LITERAL);

		Literal gmlResult = (Literal) evaluate(gmlSource);
		Literal geoJsonResult = (Literal) evaluate(geoJsonSource);

		assertEquals(GeoConstants.GEO_GML_LITERAL, gmlResult.getDatatype());
		assertEquals(CRS84,
				JenaGeometryAdapter.toSourceGeometryLiteral(gmlResult).effectiveCrsUri());
		assertEquals(100.0, JenaGeometryAdapter.toSourceGeometryLiteral(gmlResult)
				.asGeometryWrapper().getParsingGeometry().getCoordinate().x, 0.0);
		assertEquals(GeoConstants.GEO_JSON_LITERAL, geoJsonResult.getDatatype());
		assertEquals(5.0, JenaGeometryAdapter.toSourceGeometryLiteral(geoJsonResult)
				.asGeometryWrapper().getParsingGeometry().getCoordinate().getZ(), 0.0);
	}

	private Value evaluate(Value... args) throws ValueExprEvaluationException {
		GeoSparqlFunctionRegistration.registerAll();
		Function function = FunctionRegistry.getInstance().get(URI)
				.orElseThrow(() -> new AssertionError("Function not registered: " + URI));
		return function.evaluate(TRIPLE_SOURCE, args);
	}

	private Literal wkt(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL);
	}
}
