package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TestSpatialAggregates extends AbstractGeoSparqlPluginTest {
	private static final String PREFIXES = """
			PREFIX geo: <http://www.opengis.net/ont/geosparql#>
			PREFIX geof: <http://www.opengis.net/def/function/geosparql/>
			PREFIX ex: <http://example.com/spatial-aggregates/>
			""";

	@Before
	public void insertRegions() {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:a1 ex:region ex:north ; geo:hasGeometry [ geo:asWKT "POLYGON((0 0,2 0,2 2,0 2,0 0))"^^geo:wktLiteral ] .
				  ex:a2 ex:region ex:north ; geo:hasGeometry [ geo:asWKT "POLYGON((1 0,3 0,3 2,1 2,1 0))"^^geo:wktLiteral ] .
				  ex:b1 ex:region ex:south ; geo:hasGeometry [ geo:asWKT "POINT(10 10)"^^geo:wktLiteral ] .
				}
				""");
	}

	@Test
	public void unionAggregatesGeometriesPerGroup() throws Exception {
		Map<String, Geometry> results = aggregatePerRegion("geof:aggUnion(?wkt)");

		assertTrue(results.get("north").toText(), results.get("north").equalsTopo(
				new WKTReader().read("POLYGON((0 0,3 0,3 2,0 2,0 0))")));
		assertTrue(results.get("south").toText(), results.get("south").equalsTopo(
				new WKTReader().read("POINT(10 10)")));
	}

	@Test
	public void everySpatialAggregateReturnsAGeometryLiteralPerGroup() {
		for (String aggregate : List.of("aggBoundingBox", "aggBoundingCircle", "aggCentroid", "aggCollect",
				"aggConcaveHull", "aggConvexHull", "aggUnion")) {
			Map<String, Geometry> results = aggregatePerRegion("geof:" + aggregate + "(?wkt)");

			assertNotNull(aggregate, results.get("north"));
			assertNotNull(aggregate, results.get("south"));
		}
	}

	@Test
	public void distinctAggregatesEachGeometryLiteralOnce() {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA { ex:a3 ex:region ex:north ;
				  geo:hasGeometry [ geo:asWKT "POLYGON((0 0,2 0,2 2,0 2,0 0))"^^geo:wktLiteral ] . }
				""");

		assertEquals(3, aggregatePerRegion("geof:aggCollect(?wkt)").get("north").getNumGeometries());
		assertEquals(2, aggregatePerRegion("geof:aggCollect(DISTINCT ?wkt)").get("north").getNumGeometries());
	}

	@Test
	public void invalidGeometryLeavesOnlyItsGroupUnbound() {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA { ex:b2 ex:region ex:south ;
				  geo:hasGeometry [ geo:asWKT "POINT(not a number)"^^geo:wktLiteral ] . }
				""");

		Map<String, Geometry> results = aggregatePerRegion("geof:aggUnion(?wkt)");

		assertNotNull(results.get("north"));
		assertNull(results.get("south"));
	}

	/**
	 * Dependency limitation: GraphDB 11.0 bundles the RDF4J 5.1.2 SPARQL parser, which rejects custom aggregates
	 * with more than one argument, and GraphDB 11.5.1 evaluates only the first argument of a custom aggregate. Once
	 * GraphDB evaluates every argument, this query returns the concave hull with the optional {@code targetPercent}
	 * 1, which is the convex hull of each group.
	 */
	@Test
	public void concaveHullAggregateWithTargetPercentIsRejectedByQueryParser() {
		IllegalArgumentException rejection = assertThrows(IllegalArgumentException.class,
				() -> aggregatePerRegion("geof:aggConcaveHull(?wkt, 1.0)"));

		assertEquals("Custom aggregate functions cannot have more than one argument", rejection.getMessage());
	}

	private Map<String, Geometry> aggregatePerRegion(String aggregate) {
		Map<String, Geometry> results = new LinkedHashMap<>();
		try (TupleQueryResult result = connection.prepareTupleQuery(QueryLanguage.SPARQL, PREFIXES
				+ "SELECT ?region (" + aggregate + " AS ?result) WHERE {"
				+ " ?feature ex:region ?region ; geo:hasGeometry/geo:asWKT ?wkt }"
				+ " GROUP BY ?region").evaluate()) {
			for (BindingSet bindings : result) {
				String region = bindings.getValue("region").stringValue().replaceFirst(".*/", "");
				Value value = bindings.getValue("result");
				results.put(region, value == null ? null
						: JenaGeometryAdapter.toSourceGeometryLiteral((Literal) value).asGeometryWrapper()
								.getXYGeometry());
			}
		}
		return results;
	}
}
