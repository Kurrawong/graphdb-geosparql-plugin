package com.ontotext.trree.geosparql;

import org.eclipse.rdf4j.query.QueryLanguage;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TestKmlIndexing extends AbstractGeoSparqlPluginTest {
	private static final String NS = "http://example.com/kml-indexing/";
	private static final String PREFIXES = """
			PREFIX geo: <http://www.opengis.net/ont/geosparql#>
			PREFIX ex: <http://example.com/kml-indexing/>
			""";

	@Test
	public void enableTimeFullIndexMakesKmlOnlyGeometryAndFeatureQueryable() throws Exception {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:container a geo:Feature ; geo:hasDefaultGeometry ex:containerGeom .
				  ex:containerGeom a geo:Geometry ;
				    geo:asKML '''<Polygon xmlns="http://www.opengis.net/kml/2.2"><outerBoundaryIs><LinearRing>
				      <coordinates>0,0 4,0 4,4 0,4 0,0</coordinates></LinearRing></outerBoundaryIs></Polygon>'''^^geo:kmlLiteral .
				  ex:thing a geo:Feature ; geo:hasDefaultGeometry ex:thingGeom .
				  ex:thingGeom a geo:Geometry ;
				    geo:asKML '''<Point><coordinates>1,1</coordinates></Point>''' .
				}
				""");

		enablePlugin();

		assertCandidateLookupFindsThing();
		assertTrue(ask("ex:thingGeom geo:sfWithin ex:containerGeom"));
		assertTrue(ask("ex:thing geo:sfWithin ex:container"));
	}

	@Test
	public void addingKmlIndexesGeometryAndReferencingFeature() throws Exception {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:container a geo:Feature ; geo:hasDefaultGeometry ex:containerGeom .
				  ex:containerGeom a geo:Geometry ;
				    geo:asWKT "POLYGON((0 0,0 4,4 4,4 0,0 0))"^^geo:wktLiteral .
				  ex:thing a geo:Feature ; geo:hasDefaultGeometry ex:thingGeom .
				  ex:thingGeom a geo:Geometry .
				}
				""");
		enablePlugin();

		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:thingGeom geo:asKML '''<Point><coordinates>1,1</coordinates></Point>'''^^geo:kmlLiteral .
				}
				""");

		assertCandidateLookupFindsThing();
		assertTrue(ask("ex:thingGeom geo:sfWithin ex:containerGeom"));
		assertTrue(ask("ex:thing geo:sfWithin ex:container"));
	}

	@Test
	public void kmlLiteralIsIndexedThroughHasSerialization() throws Exception {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:container a geo:Feature ; geo:hasDefaultGeometry ex:containerGeom .
				  ex:containerGeom a geo:Geometry ;
				    geo:asWKT "POLYGON((0 0,0 4,4 4,4 0,0 0))"^^geo:wktLiteral .
				  ex:thing a geo:Feature ; geo:hasDefaultGeometry ex:thingGeom .
				  ex:thingGeom a geo:Geometry ;
				    geo:hasSerialization '''<Point><coordinates>1,1</coordinates></Point>'''^^geo:kmlLiteral .
				}
				""");

		enablePlugin();

		assertCandidateLookupFindsThing();
		assertTrue(ask("ex:thingGeom geo:sfWithin ex:containerGeom"));
		assertTrue(ask("ex:thing geo:sfWithin ex:container"));
	}

	/**
	 * Uses one bound relation operand so the relation is answered through index candidate lookup.
	 */
	private void assertCandidateLookupFindsThing() throws Exception {
		assertTrue(executeSparqlQueryWithResult(PREFIXES
				+ "SELECT ?geometry WHERE { ?geometry geo:sfWithin ex:containerGeom }", "geometry")
				.contains(VF.createIRI(NS + "thingGeom")));
		assertTrue(executeSparqlQueryWithResult(PREFIXES
				+ "SELECT ?feature WHERE { ?feature geo:sfWithin ex:container }", "feature")
				.contains(VF.createIRI(NS + "thing")));
	}

	private boolean ask(String pattern) {
		return connection.prepareBooleanQuery(QueryLanguage.SPARQL, PREFIXES + "ASK { " + pattern + " }").evaluate();
	}
}
