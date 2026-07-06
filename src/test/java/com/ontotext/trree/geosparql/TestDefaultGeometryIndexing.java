package com.ontotext.trree.geosparql;

import org.eclipse.rdf4j.query.QueryLanguage;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestDefaultGeometryIndexing extends AbstractGeoSparqlPluginTest {
	private static final String PREFIXES = ""
			+ "PREFIX geo: <http://www.opengis.net/ont/geosparql#>\n"
			+ "PREFIX ex: <http://example.com/default-geometry-indexing/>\n";

	@Test
	public void geometryLiteralUpdateReindexesGeometryAndReferencingFeature() throws Exception {
		insertContainerAndFeature("POINT(1 1)");
		enablePlugin();

		assertTrue(ask("ex:thing geo:sfWithin ex:container"));
		assertTrue(ask("ex:thingGeom geo:sfWithin ex:container"));

		executeSparqlUpdateQuery(PREFIXES
				+ "DELETE { ex:thingGeom geo:asWKT \"POINT(1 1)\"^^geo:wktLiteral }\n"
				+ "INSERT { ex:thingGeom geo:asWKT \"POINT(9 9)\"^^geo:wktLiteral }\n"
				+ "WHERE { ex:thingGeom geo:asWKT \"POINT(1 1)\"^^geo:wktLiteral }");

		assertFalse(ask("ex:thing geo:sfWithin ex:container"));
		assertFalse(ask("ex:thingGeom geo:sfWithin ex:container"));
	}

	@Test
	public void defaultGeometryUpdateReindexesFeature() throws Exception {
		executeSparqlUpdateQuery(PREFIXES
				+ "INSERT DATA {\n"
				+ "  ex:container a geo:Feature ; geo:hasDefaultGeometry ex:containerGeom .\n"
				+ "  ex:containerGeom a geo:Geometry ;\n"
				+ "    geo:asWKT \"POLYGON((0 0,0 4,4 4,4 0,0 0))\"^^geo:wktLiteral .\n"
				+ "  ex:thing a geo:Feature ; geo:hasDefaultGeometry ex:insideGeom .\n"
				+ "  ex:insideGeom a geo:Geometry ; geo:asWKT \"POINT(1 1)\"^^geo:wktLiteral .\n"
				+ "  ex:outsideGeom a geo:Geometry ; geo:asWKT \"POINT(9 9)\"^^geo:wktLiteral .\n"
				+ "}");
		enablePlugin();

		assertTrue(ask("ex:thing geo:sfWithin ex:container"));

		executeSparqlUpdateQuery(PREFIXES
				+ "DELETE { ex:thing geo:hasDefaultGeometry ex:insideGeom }\n"
				+ "INSERT { ex:thing geo:hasDefaultGeometry ex:outsideGeom }\n"
				+ "WHERE { ex:thing geo:hasDefaultGeometry ex:insideGeom }");

		assertFalse(ask("ex:thing geo:sfWithin ex:container"));
	}

	private void insertContainerAndFeature(String featurePointWkt) {
		executeSparqlUpdateQuery(PREFIXES
				+ "INSERT DATA {\n"
				+ "  ex:container a geo:Feature ; geo:hasDefaultGeometry ex:containerGeom .\n"
				+ "  ex:containerGeom a geo:Geometry ;\n"
				+ "    geo:asWKT \"POLYGON((0 0,0 4,4 4,4 0,0 0))\"^^geo:wktLiteral .\n"
				+ "  ex:thing a geo:Feature ; geo:hasDefaultGeometry ex:thingGeom .\n"
				+ "  ex:thingGeom a geo:Geometry ; geo:asWKT \"" + featurePointWkt + "\"^^geo:wktLiteral .\n"
				+ "}");
	}

	private boolean ask(String pattern) {
		return connection.prepareBooleanQuery(QueryLanguage.SPARQL, PREFIXES + "ASK { " + pattern + " }").evaluate();
	}
}
