package com.ontotext.trree.geosparql;

import org.eclipse.rdf4j.query.QueryLanguage;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TestKmlIndexing extends AbstractGeoSparqlPluginTest {
	private static final String PREFIXES = """
			PREFIX geo: <http://www.opengis.net/ont/geosparql#>
			PREFIX ex: <http://example.com/kml-indexing/>
			""";

	@Test
	public void enableTimeFullIndexMakesKmlOnlyGeometryAndFeatureQueryable() {
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

		assertTrue(ask("ex:thingGeom geo:sfWithin ex:containerGeom"));
		assertTrue(ask("ex:thing geo:sfWithin ex:container"));
	}

	@Test
	public void addingKmlIndexesGeometryAndReferencingFeature() {
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

		assertTrue(ask("ex:thingGeom geo:sfWithin ex:containerGeom"));
		assertTrue(ask("ex:thing geo:sfWithin ex:container"));
	}

	@Test
	public void kmlLiteralIsIndexedThroughHasSerialization() {
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

		assertTrue(ask("ex:thingGeom geo:sfWithin ex:containerGeom"));
		assertTrue(ask("ex:thing geo:sfWithin ex:container"));
	}

	private boolean ask(String pattern) {
		return connection.prepareBooleanQuery(QueryLanguage.SPARQL, PREFIXES + "ASK { " + pattern + " }").evaluate();
	}
}
