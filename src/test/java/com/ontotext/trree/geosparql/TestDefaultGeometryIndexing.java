package com.ontotext.trree.geosparql;

import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

	@Test
	public void propertyRelationReturnsOneRowWhenSubjectHasMultipleMatchingDefaultGeometries() throws Exception {
		insertSubjectWithMultipleMatchingDefaultGeometries();
		enablePlugin();

		assertEquals(1, count("SELECT ?s WHERE {\n"
				+ "  ?s geo:sfWithin ex:container .\n"
				+ "  FILTER(?s = ex:thing)\n"
				+ "}"));
	}

	@Test
	public void propertyRelationReturnsOneRowWhenObjectHasMultipleMatchingDefaultGeometries() throws Exception {
		executeSparqlUpdateQuery(PREFIXES
				+ "INSERT DATA {\n"
				+ "  ex:container a geo:Feature ;\n"
				+ "    geo:hasDefaultGeometry ex:containerGeom1, ex:containerGeom2 .\n"
				+ "  ex:containerGeom1 a geo:Geometry ;\n"
				+ "    geo:asWKT \"POLYGON((0 0,0 4,4 4,4 0,0 0))\"^^geo:wktLiteral .\n"
				+ "  ex:containerGeom2 a geo:Geometry ;\n"
				+ "    geo:asWKT \"POLYGON((0 0,0 5,5 5,5 0,0 0))\"^^geo:wktLiteral .\n"
				+ "  ex:thing a geo:Feature ; geo:hasDefaultGeometry ex:thingGeom .\n"
				+ "  ex:thingGeom a geo:Geometry ; geo:asWKT \"POINT(1 1)\"^^geo:wktLiteral .\n"
				+ "}");
		enablePlugin();

		assertEquals(1, count("SELECT ?o WHERE {\n"
				+ "  ex:thing geo:sfWithin ?o .\n"
				+ "  FILTER(?o = ex:container)\n"
				+ "}"));
	}

	@Test
	public void propertyRelationReturnsOneRowWhenBothSidesAreBoundAndMultipleGeometryPairsMatch() throws Exception {
		insertBothSidesWithOnlyLaterGeometryPairMatching();
		enablePlugin();

		assertEquals(1, count("SELECT ?match WHERE {\n"
				+ "  ex:thing geo:sfWithin ex:container .\n"
				+ "  BIND(\"match\" AS ?match)\n"
				+ "}"));
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

	private void insertSubjectWithMultipleMatchingDefaultGeometries() {
		executeSparqlUpdateQuery(PREFIXES
				+ "INSERT DATA {\n"
				+ "  ex:container a geo:Feature ; geo:hasDefaultGeometry ex:containerGeom .\n"
				+ "  ex:containerGeom a geo:Geometry ;\n"
				+ "    geo:asWKT \"POLYGON((0 0,0 4,4 4,4 0,0 0))\"^^geo:wktLiteral .\n"
				+ "  ex:thing a geo:Feature ;\n"
				+ "    geo:hasDefaultGeometry ex:insideGeom1, ex:insideGeom2 .\n"
				+ "  ex:insideGeom1 a geo:Geometry ; geo:asWKT \"POINT(1 1)\"^^geo:wktLiteral .\n"
				+ "  ex:insideGeom2 a geo:Geometry ; geo:asWKT \"POINT(2 2)\"^^geo:wktLiteral .\n"
				+ "}");
	}

	private void insertBothSidesWithOnlyLaterGeometryPairMatching() {
		executeSparqlUpdateQuery(PREFIXES
				+ "INSERT DATA {\n"
				+ "  ex:container a geo:Feature ;\n"
				+ "    geo:hasDefaultGeometry ex:containerMissGeom, ex:containerHitGeom .\n"
				+ "  ex:containerMissGeom a geo:Geometry ;\n"
				+ "    geo:asWKT \"POLYGON((10 10,10 12,12 12,12 10,10 10))\"^^geo:wktLiteral .\n"
				+ "  ex:containerHitGeom a geo:Geometry ;\n"
				+ "    geo:asWKT \"POLYGON((0 0,0 4,4 4,4 0,0 0))\"^^geo:wktLiteral .\n"
				+ "  ex:thing a geo:Feature ;\n"
				+ "    geo:hasDefaultGeometry ex:outsideGeom, ex:insideGeom .\n"
				+ "  ex:outsideGeom a geo:Geometry ; geo:asWKT \"POINT(8 8)\"^^geo:wktLiteral .\n"
				+ "  ex:insideGeom a geo:Geometry ; geo:asWKT \"POINT(1 1)\"^^geo:wktLiteral .\n"
				+ "}");
	}

	private boolean ask(String pattern) {
		return connection.prepareBooleanQuery(QueryLanguage.SPARQL, PREFIXES + "ASK { " + pattern + " }").evaluate();
	}

	private int count(String query) throws Exception {
		TupleQueryResult result = connection.prepareTupleQuery(QueryLanguage.SPARQL, PREFIXES + query).evaluate();
		try {
			int count = 0;
			while (result.hasNext()) {
				result.next();
				count++;
			}
			return count;
		} finally {
			result.close();
		}
	}
}
