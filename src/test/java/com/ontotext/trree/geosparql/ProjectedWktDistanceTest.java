package com.ontotext.trree.geosparql;

import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies measurements in the source EPSG:3006 easting/northing CRS using a 3–4–5 right triangle.
 * The fixture retains the two supplied points and adds the right-angle corner at the same height.
 */
public class ProjectedWktDistanceTest extends AbstractGeoSparqlPluginTest {
	/**
	 * Both distance functions measure the projected easting/northing deltas, returning 5 metres.
	 */
	@Test
	public void projectedWktDistanceUsesSourceCrs() throws Exception {
		importData("issues/epsg-3006-distance-345.ttl", RDFFormat.TURTLE);

		String query = """
				PREFIX geo: <http://www.opengis.net/ont/geosparql#>
				PREFIX geof: <http://www.opengis.net/def/function/geosparql/>
				PREFIX uom: <http://www.opengis.net/def/uom/OGC/1.0/>
				PREFIX data: <http://www.triona.se/data#>

				SELECT * WHERE {
				  data:obj3 geo:hasGeometry/geo:asWKT ?geom1 .
				  data:obj4 geo:hasGeometry/geo:asWKT ?geom2 .
				  BIND(geof:distance(?geom1, ?geom2, uom:metre) AS ?distance)
				  BIND(geof:metricDistance(?geom1, ?geom2) AS ?metricDistance)
				}
				""";

		try (TupleQueryResult result = connection.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()) {
			assertTrue(result.hasNext());
			BindingSet bindings = result.next();
			assertFalse(result.hasNext());
			assertTrue(bindings.hasBinding("distance"));
			assertEquals(5.0, ((Literal) bindings.getValue("distance")).doubleValue(), 0.01);
			assertTrue(bindings.hasBinding("metricDistance"));
			Literal metricDistance = (Literal) bindings.getValue("metricDistance");
			assertEquals(XSD.DOUBLE, metricDistance.getDatatype());
			assertEquals(5.0, metricDistance.doubleValue(), 1e-9);
		}
	}

	@Test
	public void projectedTriangleMetricAreaAndPerimeterUseSourceCrs() throws Exception {
		importData("issues/epsg-3006-distance-345.ttl", RDFFormat.TURTLE);

		String query = """
				PREFIX geo: <http://www.opengis.net/ont/geosparql#>
				PREFIX geof: <http://www.opengis.net/def/function/geosparql/>
				PREFIX data: <http://www.triona.se/data#>

				SELECT * WHERE {
				  data:triangle geo:hasGeometry/geo:asWKT ?geom .
				  BIND(geof:metricArea(?geom) AS ?area)
				  BIND(geof:metricPerimeter(?geom) AS ?perimeter)
				}
				""";

		try (TupleQueryResult result = connection.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()) {
			assertTrue(result.hasNext());
			BindingSet bindings = result.next();
			assertFalse(result.hasNext());
			assertTrue(bindings.hasBinding("area"));
			Literal area = (Literal) bindings.getValue("area");
			assertEquals(XSD.DOUBLE, area.getDatatype());
			assertEquals(6.0, area.doubleValue(), 1e-9);
			assertTrue(bindings.hasBinding("perimeter"));
			Literal perimeter = (Literal) bindings.getValue("perimeter");
			assertEquals(XSD.DOUBLE, perimeter.getDatatype());
			assertEquals(12.0, perimeter.doubleValue(), 1e-9);
		}
	}
}
