package com.ontotext.trree.geosparql;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TestHasSerializationIndexing extends AbstractGeoSparqlPluginTest {
	private static final String PREFIXES = """
			PREFIX geo: <http://www.opengis.net/ont/geosparql#>
			PREFIX ex: <http://example.com/has-serialization-indexing/>
			""";

	@Test
	public void fullIndexUsesSupportedGenericLiteralDatatypesForGeometriesAndFeatures() throws Exception {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:container a geo:Feature ; geo:hasDefaultGeometry ex:containerGeom .
				  ex:containerGeom a geo:Geometry ;
				    geo:asWKT "POLYGON((0 0,0 4,4 4,4 0,0 0))"^^geo:wktLiteral .
				  ex:wktFeature a geo:Feature ; geo:hasDefaultGeometry ex:wktGeom .
				  ex:wktGeom geo:hasSerialization "POINT(1 1)"^^geo:wktLiteral .
				  ex:gmlFeature a geo:Feature ; geo:hasDefaultGeometry ex:gmlGeom .
				  ex:gmlGeom geo:hasSerialization '''
				    <gml:Point xmlns:gml="http://www.opengis.net/gml/3.2"
				        srsName="http://www.opengis.net/def/crs/OGC/1.3/CRS84">
				      <gml:pos>2 2</gml:pos>
				    </gml:Point>
				    '''^^geo:gmlLiteral .
				  ex:geoJsonFeature a geo:Feature ; geo:hasDefaultGeometry ex:geoJsonGeom .
				  ex:geoJsonGeom geo:hasSerialization
				    '''{"type":"Point","coordinates":[3,3]}'''^^geo:geoJSONLiteral .
				  ex:otherFeature a geo:Feature ; geo:hasDefaultGeometry ex:otherGeom .
				  ex:otherGeom geo:hasSerialization "POINT(1 1)", ex:distribution .
				}
				""");
		enablePlugin();

		assertEquals(Set.of(iri("wktGeom"), iri("gmlGeom"), iri("geoJsonGeom")),
				new HashSet<>(select("geometry", """
						SELECT ?geometry WHERE {
						  VALUES ?geometry { ex:wktGeom ex:gmlGeom ex:geoJsonGeom ex:otherGeom }
						  ?geometry geo:sfWithin ex:containerGeom .
						}
						""")));
		assertEquals(Set.of(iri("wktFeature"), iri("gmlFeature"), iri("geoJsonFeature")),
				new HashSet<>(select("feature", """
						SELECT ?feature WHERE {
						  VALUES ?feature { ex:wktFeature ex:gmlFeature ex:geoJsonFeature ex:otherFeature }
						  ?feature geo:sfWithin ex:container .
						}
						""")));
	}

	@Test
	public void incrementalChangesToGenericSerializationUpdateGeometryAndFeatureRelations() throws Exception {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:container a geo:Feature ; geo:hasDefaultGeometry ex:containerGeom .
				  ex:containerGeom geo:asWKT "POLYGON((0 0,0 4,4 4,4 0,0 0))"^^geo:wktLiteral .
				  ex:thing a geo:Feature ; geo:hasDefaultGeometry ex:thingGeom .
				}
				""");
		enablePlugin();

		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA { ex:thingGeom geo:hasSerialization "POINT(1 1)"^^geo:wktLiteral }
				""");
		assertWithin(true);

		executeSparqlUpdateQuery(PREFIXES + """
				DELETE { ex:thingGeom geo:hasSerialization "POINT(1 1)"^^geo:wktLiteral }
				INSERT { ex:thingGeom geo:hasSerialization
				  '''{"type":"Point","coordinates":[9,9]}'''^^geo:geoJSONLiteral }
				WHERE { ex:thingGeom geo:hasSerialization "POINT(1 1)"^^geo:wktLiteral }
				""");
		assertWithin(false);
		forceReindex();
		assertWithin(false);

		executeSparqlUpdateQuery(PREFIXES + """
				DELETE { ex:thingGeom geo:hasSerialization
				  '''{"type":"Point","coordinates":[9,9]}'''^^geo:geoJSONLiteral }
				INSERT { ex:thingGeom geo:hasSerialization '''
				  <gml:Point xmlns:gml="http://www.opengis.net/gml/3.2"
				      srsName="http://www.opengis.net/def/crs/OGC/1.3/CRS84">
				    <gml:pos>2 2</gml:pos>
				  </gml:Point>
				  '''^^geo:gmlLiteral }
				WHERE { ex:thingGeom geo:hasSerialization
				  '''{"type":"Point","coordinates":[9,9]}'''^^geo:geoJSONLiteral }
				""");
		assertWithin(true);

		executeSparqlUpdateQuery(PREFIXES + """
				DELETE WHERE { ex:thingGeom geo:hasSerialization ?serialization }
				""");
		assertWithin(false);
		forceReindex();
		assertWithin(false);
	}

	private void assertWithin(boolean expected) {
		assertEquals(expected, ask("ex:thingGeom geo:sfWithin ex:containerGeom"));
		assertEquals(expected, ask("ex:thing geo:sfWithin ex:container"));
	}

	private boolean ask(String pattern) {
		return connection.prepareBooleanQuery(QueryLanguage.SPARQL,
				PREFIXES + "ASK { " + pattern + " }").evaluate();
	}

	private List<Value> select(String binding, String query) throws Exception {
		return executeSparqlQueryWithResult(PREFIXES + query, binding);
	}

	private Value iri(String localName) {
		return VF.createIRI("http://example.com/has-serialization-indexing/" + localName);
	}
}
