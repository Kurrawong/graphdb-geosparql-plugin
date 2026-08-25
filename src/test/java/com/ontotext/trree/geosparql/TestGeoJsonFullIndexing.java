package com.ontotext.trree.geosparql;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TestGeoJsonFullIndexing extends AbstractGeoSparqlPluginTest {
	private static final String PREFIXES = """
			PREFIX geo: <http://www.opengis.net/ont/geosparql#>
			PREFIX ex: <http://example.com/geojson-full-indexing/>
			""";

	@Test
	public void enableTimeFullIndexMakesGeoJsonOnlyGeometryAndFeatureQueryable() throws Exception {
		insertGeoJsonOnlyContainerAndThing();

		enablePlugin();

		assertEquals(List.of(VF.createIRI("http://example.com/geojson-full-indexing/thingGeom")),
				select("geometry", """
						SELECT ?geometry WHERE {
						  ?geometry geo:sfWithin ex:containerGeom .
						  FILTER(?geometry = ex:thingGeom)
						}
						"""));
		assertEquals(List.of(VF.createIRI("http://example.com/geojson-full-indexing/thing")),
				select("feature", """
						SELECT ?feature WHERE {
						  ?feature geo:sfWithin ex:container .
						  FILTER(?feature = ex:thing)
						}
						"""));
	}

	@Test
	public void forceReindexDiscoversPreExistingGeoJsonSerializations() throws Exception {
		enablePlugin();
		insertGeoJsonOnlyContainerAndThing();

		forceReindex();

		assertEquals(1, select("feature", """
				SELECT ?feature WHERE {
				  ?feature geo:sfWithin ex:container .
				  FILTER(?feature = ex:thing)
				}
				""").size());
	}

	@Test
	public void relationUsesEveryDistinctSourceAcrossWktGmlAndGeoJson() throws Exception {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:containerGeom a geo:Geometry ;
				    geo:asWKT "POLYGON((10 10,10 14,14 14,14 10,10 10))"^^geo:wktLiteral ;
				    geo:asGML "<gml:Polygon xmlns:gml='http://www.opengis.net/gml/3.2' srsName='http://www.opengis.net/def/crs/OGC/1.3/CRS84'><gml:exterior><gml:LinearRing><gml:posList>20 20 20 24 24 24 24 20 20 20</gml:posList></gml:LinearRing></gml:exterior></gml:Polygon>"^^geo:gmlLiteral ;
				    geo:asGeoJSON '''{"type":"Polygon","coordinates":[[[0,0],[0,4],[4,4],[4,0],[0,0]]]}'''^^geo:geoJSONLiteral .
				  ex:geoJsonThing a geo:Geometry ;
				    geo:asWKT "POINT(1 1)"^^geo:wktLiteral .
				  ex:wktThing a geo:Geometry ;
				    geo:asWKT "POINT(11 11)"^^geo:wktLiteral .
				  ex:gmlThing a geo:Geometry ;
				    geo:asWKT "POINT(21 21)"^^geo:wktLiteral .
				}
				""");
		enablePlugin();

		List<Value> matches = select("geometry", """
				SELECT ?geometry WHERE {
				  ?geometry geo:sfWithin ex:containerGeom .
				  FILTER(?geometry IN (ex:geoJsonThing, ex:wktThing, ex:gmlThing))
				}
				""");

		assertEquals(3, matches.size());
		assertEquals(Set.of(
				VF.createIRI("http://example.com/geojson-full-indexing/geoJsonThing"),
				VF.createIRI("http://example.com/geojson-full-indexing/wktThing"),
				VF.createIRI("http://example.com/geojson-full-indexing/gmlThing")),
				new HashSet<>(matches));
	}

	@Test
	public void invalidGeoJsonFailsStrictFullIndexBuild() {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:invalid geo:asGeoJSON '''{"type":"Point","coordinates":[1]}'''^^geo:geoJSONLiteral .
				}
				""");

		RepositoryException exception = assertThrows(RepositoryException.class, this::enablePlugin);

		assertCauseChainContains(exception, "Could not index GeoSPARQL geometry");
		assertCauseChainContains(exception, "geoJSONLiteral");
	}

	@Test
	public void explicitNonGeometryDatatypeIsNotReinterpretedAsGeoJson() {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:invalid geo:asGeoJSON '''{"type":"Point","coordinates":[1,2]}'''^^ex:notAGeometryLiteral .
				}
				""");

		RepositoryException exception = assertThrows(RepositoryException.class, this::enablePlugin);

		assertCauseChainContains(exception, "Unsupported GeoSPARQL geometry datatype");
		assertCauseChainContains(exception, "predicate/fallback datatype: "
				+ "http://www.opengis.net/ont/geosparql#geoJSONLiteral");
	}

	@Test
	public void ignoreErrorsSkipsInvalidGeoJsonDuringFullIndexBuild() throws Exception {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:containerGeom geo:asGeoJSON '''{"type":"Polygon","coordinates":[[[0,0],[0,4],[4,4],[4,0],[0,0]]]}'''^^geo:geoJSONLiteral .
				  ex:valid geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}'''^^geo:geoJSONLiteral .
				  ex:invalid geo:asGeoJSON '''{"type":"Point","coordinates":[1]}'''^^geo:geoJSONLiteral .
				}
				""");
		executePluginControl(GeoSparqlPlugin.IGNORE_ERRORS_PREDICATE_IRI, VF.createLiteral(true));

		enablePlugin();

		assertEquals(List.of(VF.createIRI("http://example.com/geojson-full-indexing/valid")),
				select("geometry", """
						SELECT ?geometry WHERE {
						  ?geometry geo:sfWithin ex:containerGeom .
						  FILTER(?geometry IN (ex:valid, ex:invalid))
						}
						"""));
	}

	private void insertGeoJsonOnlyContainerAndThing() {
		executeSparqlUpdateQuery(PREFIXES + """
				INSERT DATA {
				  ex:container a geo:Feature ; geo:hasDefaultGeometry ex:containerGeom .
				  ex:containerGeom a geo:Geometry ;
				    geo:asGeoJSON '''{"type":"Polygon","coordinates":[[[0,0],[0,4],[4,4],[4,0],[0,0]]]}'''^^geo:geoJSONLiteral .
				  ex:thing a geo:Feature ; geo:hasDefaultGeometry ex:thingGeom .
				  ex:thingGeom a geo:Geometry ;
				    geo:asGeoJSON '''{"type":"Point","coordinates":[1,1]}''' .
				}
				""");
	}

	private List<Value> select(String binding, String query) throws Exception {
		return executeSparqlQueryWithResult(PREFIXES + query, binding);
	}

}
