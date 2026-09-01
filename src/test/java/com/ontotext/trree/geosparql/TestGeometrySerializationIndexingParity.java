package com.ontotext.trree.geosparql;

import org.eclipse.rdf4j.model.Value;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TestGeometrySerializationIndexingParity extends AbstractGeoSparqlPluginTest {
	private static final String PREFIXES = """
			PREFIX geo: <http://www.opengis.net/ont/geosparql#>
			PREFIX ex: <http://example.com/serialization-indexing-parity/>
			""";
	private static final Set<Value> EXPECTED_GEOMETRIES = Set.of(
			VF.createIRI("http://example.com/serialization-indexing-parity/wktGeom"),
			VF.createIRI("http://example.com/serialization-indexing-parity/gmlGeom"),
			VF.createIRI("http://example.com/serialization-indexing-parity/geoJsonGeom"));
	private static final Set<Value> EXPECTED_FEATURES = Set.of(
			VF.createIRI("http://example.com/serialization-indexing-parity/wktFeature"),
			VF.createIRI("http://example.com/serialization-indexing-parity/gmlFeature"),
			VF.createIRI("http://example.com/serialization-indexing-parity/geoJsonFeature"));

	@Test
	public void fullIndexingGivesWktGmlAndGeoJsonEquivalentPropertyRelations() throws Exception {
		insertGeometryResources();
		insertGeometrySerializations();

		enablePlugin();

		assertDirectGeometryAndFeatureMatches();
	}

	@Test
	public void incrementalAdditionsGiveWktGmlAndGeoJsonEquivalentPropertyRelations() throws Exception {
		insertGeometryResources();
		enablePlugin();

		insertGeometrySerializations();

		assertDirectGeometryAndFeatureMatches();
	}

	private void insertGeometryResources() {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:container a geo:Feature ; geo:hasDefaultGeometry ex:containerGeom .
				  ex:containerGeom a geo:Geometry ;
				    geo:asWKT "POLYGON((0 0,0 4,4 4,4 0,0 0))"^^geo:wktLiteral .
				  ex:wktFeature a geo:Feature ; geo:hasDefaultGeometry ex:wktGeom .
				  ex:wktGeom a geo:Geometry .
				  ex:gmlFeature a geo:Feature ; geo:hasDefaultGeometry ex:gmlGeom .
				  ex:gmlGeom a geo:Geometry .
				  ex:geoJsonFeature a geo:Feature ; geo:hasDefaultGeometry ex:geoJsonGeom .
				  ex:geoJsonGeom a geo:Geometry .
				}
				""");
	}

	private void insertGeometrySerializations() {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:wktGeom geo:asWKT "POINT(1 1)"^^geo:wktLiteral .
				  ex:gmlGeom geo:asGML '''
				    <gml:Point xmlns:gml="http://www.opengis.net/gml/3.2"
				        srsName="http://www.opengis.net/def/crs/OGC/1.3/CRS84">
				      <gml:pos>2 2</gml:pos>
				    </gml:Point>
				    '''^^geo:gmlLiteral .
				  ex:geoJsonGeom geo:asGeoJSON '''{"type":"Point","coordinates":[3,3]}'''^^geo:geoJSONLiteral .
				}
				""");
	}

	private void assertDirectGeometryAndFeatureMatches() throws Exception {
		assertEquals(EXPECTED_GEOMETRIES, new HashSet<>(select("geometry", """
				SELECT ?geometry WHERE {
				  VALUES ?geometry { ex:wktGeom ex:gmlGeom ex:geoJsonGeom }
				  ?geometry geo:sfWithin ex:containerGeom .
				}
				""")));
		assertEquals(EXPECTED_FEATURES, new HashSet<>(select("feature", """
				SELECT ?feature WHERE {
				  VALUES ?feature { ex:wktFeature ex:gmlFeature ex:geoJsonFeature }
				  ?feature geo:sfWithin ex:container .
				}
				""")));
	}

	private List<Value> select(String binding, String query) throws Exception {
		return executeSparqlQueryWithResult(PREFIXES + query, binding);
	}
}
