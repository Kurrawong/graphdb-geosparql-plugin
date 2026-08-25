package com.ontotext.trree.geosparql;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TestGeoJsonIncrementalIndexing extends AbstractGeoSparqlPluginTest {
	private static final String PREFIXES = """
			PREFIX geo: <http://www.opengis.net/ont/geosparql#>
			PREFIX ex: <http://example.com/geojson-incremental-indexing/>
			""";

	@Test
	public void addingGeoJsonIndexesGeometryAndReferencingFeature() throws Exception {
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
				  ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral .
				}
				""");

		assertEquals(List.of(VF.createIRI("http://example.com/geojson-incremental-indexing/thingGeom")),
				select("geometry", """
						SELECT ?geometry WHERE {
						  ?geometry geo:sfWithin ex:containerGeom .
						  FILTER(?geometry = ex:thingGeom)
						}
						"""));
		assertEquals(List.of(VF.createIRI("http://example.com/geojson-incremental-indexing/thing")),
				select("feature", """
						SELECT ?feature WHERE {
						  ?feature geo:sfWithin ex:container .
						  FILTER(?feature = ex:thing)
						}
						"""));
	}

	@Test
	public void replacingAndRemovingGeoJsonMatchesFullRebuild() throws Exception {
		insertContainerAndGeoJsonThing();
		enablePlugin();
		assertThingWithinContainer(true);

		executeSparqlUpdateQuery(PREFIXES + """
				DELETE { ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral }
				INSERT { ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[9,9]}'''^^geo:geoJSONLiteral }
				WHERE { ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral }
				""");

		assertThingWithinContainer(false);
		forceReindex();
		assertThingWithinContainer(false);

		executeSparqlUpdateQuery(PREFIXES + """
				DELETE DATA {
				  ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[9,9]}'''^^geo:geoJSONLiteral .
				}
				""");

		assertThingWithinContainer(false);
		forceReindex();
		assertThingWithinContainer(false);
	}

	@Test
	public void sharedGeometryUpdateReindexesEveryReferencingFeature() throws Exception {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:containerGeom geo:asWKT "POLYGON((0 0,0 4,4 4,4 0,0 0))"^^geo:wktLiteral .
				  ex:alpha geo:hasDefaultGeometry ex:sharedGeom .
				  ex:beta geo:hasDefaultGeometry ex:sharedGeom .
				  ex:sharedGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral .
				}
				""");
		enablePlugin();

		assertEquals(2, select("feature", """
				SELECT ?feature WHERE {
				  ?feature geo:sfWithin ex:containerGeom .
				  FILTER(?feature IN (ex:alpha, ex:beta))
				}
				""").size());

		executeSparqlUpdateQuery(PREFIXES + """
				DELETE { ex:sharedGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral }
				INSERT { ex:sharedGeom geo:asGeoJSON '''{"type":"Point","coordinates":[9,9]}'''^^geo:geoJSONLiteral }
				WHERE { ex:sharedGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral }
				""");

		assertEquals(0, select("feature", """
				SELECT ?feature WHERE {
				  ?feature geo:sfWithin ex:containerGeom .
				  FILTER(?feature IN (ex:alpha, ex:beta))
				}
				""").size());
	}

	@Test
	public void multipleDefaultGeometriesAndSerializationsRetainExistentialSemantics() throws Exception {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:containerGeom geo:asWKT "POLYGON((0 0,0 4,4 4,4 0,0 0))"^^geo:wktLiteral .
				  ex:thing geo:hasDefaultGeometry ex:multiGeom, ex:otherGeom .
				}
				""");
		enablePlugin();

		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:multiGeom
				    geo:asWKT "POINT(9 9)"^^geo:wktLiteral ;
				    geo:asGML '''<gml:Point xmlns:gml="http://www.opengis.net/gml/3.2" srsName="http://www.opengis.net/def/crs/OGC/1.3/CRS84"><gml:pos>8 8</gml:pos></gml:Point>'''^^geo:gmlLiteral ;
				    geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral .
				  ex:otherGeom
				    geo:asGeoJSON '''{"type":"Point","coordinates":[7,7]}'''^^geo:geoJSONLiteral .
				}
				""");

		assertTrue(ask("ex:thing geo:sfWithin ex:containerGeom"));

		executeSparqlUpdateQuery(PREFIXES + """
				DELETE DATA {
				  ex:multiGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral .
				}
				""");

		assertFalse(ask("ex:thing geo:sfWithin ex:containerGeom"));
	}

	@Test
	public void untypedGeoJsonSnapshotSupportsExactEvaluation() throws Exception {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:solid geo:asWKT "POLYGON((0 0,0 10,10 10,10 0,0 0))"^^geo:wktLiteral .
				  ex:holed geo:asWKT "POLYGON((0 0,0 10,10 10,10 0,0 0),(4 4,6 4,6 6,4 6,4 4))"^^geo:wktLiteral .
				  ex:thing geo:hasDefaultGeometry ex:thingGeom .
				}
				""");
		enablePlugin();

		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[5,5]}''' .
				}
				""");

		assertTrue(ask("ex:thingGeom geo:sfWithin ex:solid"));
		assertTrue(ask("ex:thing geo:sfWithin ex:solid"));
		assertFalse(ask("ex:thingGeom geo:sfIntersects ex:holed"));
		assertFalse(ask("ex:thing geo:sfIntersects ex:holed"));
	}

	@Test
	public void rolledBackGeoJsonReplacementLeavesRdfAndIndexUnchanged() throws Exception {
		insertContainerAndGeoJsonThing();
		enablePlugin();

		connection.begin();
		try {
			connection.prepareUpdate(QueryLanguage.SPARQL, PREFIXES + """
					DELETE { ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral }
					INSERT { ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[9,9]}'''^^geo:geoJSONLiteral }
					WHERE { ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral }
					""").execute();
		} finally {
			connection.rollback();
		}

		assertThingWithinContainer(true);
		assertTrue(ask("ex:thingGeom geo:asGeoJSON '''{\"type\":\"Point\",\"coordinates\":[1,1]}'''^^geo:geoJSONLiteral"));
		assertFalse(ask("ex:thingGeom geo:asGeoJSON '''{\"type\":\"Point\",\"coordinates\":[9,9]}'''^^geo:geoJSONLiteral"));
	}

	@Test
	public void strictInvalidReplacementRestoresRdfAndIndexState() throws Exception {
		insertContainerAndGeoJsonThing();
		enablePlugin();

		connection.begin();
		connection.prepareUpdate(QueryLanguage.SPARQL, PREFIXES + """
				DELETE { ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral }
				INSERT { ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1]}'''^^geo:geoJSONLiteral }
				WHERE { ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral }
				""").execute();

		RepositoryException failure = assertThrows(RepositoryException.class, connection::commit);
		assertCauseChainContains(failure, "Could not index GeoSPARQL geometry");

		try (RepositoryConnection readConnection = repository.getConnection()) {
			assertTrue(ask(readConnection,
					"ex:thingGeom geo:asGeoJSON '''{\"type\":\"Point\",\"coordinates\":[1,1]}'''^^geo:geoJSONLiteral"));
			assertFalse(ask(readConnection,
					"ex:thingGeom geo:asGeoJSON '''{\"type\":\"Point\",\"coordinates\":[1]}'''^^geo:geoJSONLiteral"));
			assertTrue(ask(readConnection, "ex:thing geo:sfWithin ex:container"));
			assertTrue(ask(readConnection, "ex:thingGeom geo:sfWithin ex:containerGeom"));
		}
	}

	@Test
	public void ignoreErrorsSkipsInvalidGeoJsonDuringAddReplaceAndRemove() throws Exception {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:containerGeom geo:asWKT "POLYGON((0 0,0 4,4 4,4 0,0 0))"^^geo:wktLiteral .
				  ex:thing geo:hasDefaultGeometry ex:thingGeom .
				}
				""");
		enablePlugin();
		executePluginControl(GeoSparqlPlugin.IGNORE_ERRORS_PREDICATE_IRI, VF.createLiteral(true));

		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:thingGeom
				    geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral ;
				    geo:asGeoJSON '''{"type":"Point","coordinates":[1]}'''^^geo:geoJSONLiteral .
				}
				""");
		assertTrue(ask("ex:thing geo:sfWithin ex:containerGeom"));

		executeSparqlUpdateQuery(PREFIXES + """
				DELETE { ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral }
				INSERT { ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[2]}'''^^geo:geoJSONLiteral }
				WHERE { ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral }
				""");
		assertFalse(ask("ex:thing geo:sfWithin ex:containerGeom"));

		executeSparqlUpdateQuery(PREFIXES + """
				DELETE DATA {
				  ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[1]}'''^^geo:geoJSONLiteral .
				}
				""");
		assertFalse(ask("ex:thing geo:sfWithin ex:containerGeom"));

		executeSparqlUpdateQuery(PREFIXES + """
				DELETE DATA {
				  ex:thingGeom geo:asGeoJSON '''{"type":"Point","coordinates":[2]}'''^^geo:geoJSONLiteral .
				}
				""");
		assertFalse(ask("ex:thing geo:sfWithin ex:containerGeom"));
	}

	private void insertContainerAndGeoJsonThing() {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:container a geo:Feature ; geo:hasDefaultGeometry ex:containerGeom .
				  ex:containerGeom a geo:Geometry ;
				    geo:asWKT "POLYGON((0 0,0 4,4 4,4 0,0 0))"^^geo:wktLiteral .
				  ex:thing a geo:Feature ; geo:hasDefaultGeometry ex:thingGeom .
				  ex:thingGeom a geo:Geometry ;
				    geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral .
				}
				""");
	}

	private void assertThingWithinContainer(boolean expected) {
		assertEquals(expected, ask("ex:thing geo:sfWithin ex:container"));
		assertEquals(expected, ask("ex:thingGeom geo:sfWithin ex:containerGeom"));
	}

	private boolean ask(String pattern) {
		return ask(connection, pattern);
	}

	private boolean ask(RepositoryConnection repositoryConnection, String pattern) {
		return repositoryConnection.prepareBooleanQuery(QueryLanguage.SPARQL,
				PREFIXES + "ASK { " + pattern + " }").evaluate();
	}

	private List<Value> select(String binding, String query) throws Exception {
		return executeSparqlQueryWithResult(PREFIXES + query, binding);
	}
}
