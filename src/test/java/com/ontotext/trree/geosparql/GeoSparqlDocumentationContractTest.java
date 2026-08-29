package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeoSparqlDocumentationContractTest extends AbstractGeoSparqlPluginTest {
	private static final String SERIALIZATION_GUIDE = "docs/geosparql-geometry-serialization.md";
	private static final String FUNCTIONS_AND_PREDICATES_REFERENCE =
			"docs/geosparql-functions-and-predicates.md";
	private static final String PREFIXES = ""
			+ "PREFIX geo: <http://www.opengis.net/ont/geosparql#>\n"
			+ "PREFIX geof: <http://www.opengis.net/def/function/geosparql/>\n"
			+ "PREFIX ex: <http://example.com/geosparql-docs-contract/>\n";
	private static final String DOCUMENTED_FORCE_REINDEX_UPDATE = ""
			+ "PREFIX plugin: <http://www.ontotext.com/plugins/geosparql#>\n"
			+ "\n"
			+ "INSERT DATA {\n"
			+ "  [] plugin:forceReindex true\n"
			+ "}";

	@Test
	public void readmeContainsRunnableForceReindexUpdate() throws IOException {
		String readme = Files.readString(Path.of("README.md"));

		assertTrue(readme.contains(DOCUMENTED_FORCE_REINDEX_UPDATE));
		enablePlugin();
		executeSparqlUpdateQuery(DOCUMENTED_FORCE_REINDEX_UPDATE);
	}

	@Test
	public void readmeLinksTheBoundedGeometrySerializationContract() throws IOException {
		String readme = Files.readString(Path.of("README.md"));
		String guide = Files.readString(Path.of(SERIALIZATION_GUIDE));

		assertTrue(readme.contains("[Geometry serialization and conversion]("
				+ SERIALIZATION_GUIDE + ")"));
		assertTrue(guide.contains("geo:geoJSONLiteral"));
		assertTrue(guide.contains("geof:asGeoJSON"));
		assertTrue(guide.contains("geof:asWKT"));
		assertTrue(guide.contains("geof:asGML"));
		assertTrue(guide.contains("geof:asKML"));
		assertTrue(guide.contains("geof:asDGGS"));
		assertTrue(guide.contains("does not claim complete GeoSPARQL 1.1 conformance"));
	}

	@Test
	public void readmeLinksTheFunctionsAndPredicatesReference() throws IOException {
		String readme = Files.readString(Path.of("README.md"));
		String reference = Files.readString(Path.of(FUNCTIONS_AND_PREDICATES_REFERENCE));

		assertTrue(readme.contains("[GeoSPARQL functions and predicates reference]("
				+ FUNCTIONS_AND_PREDICATES_REFERENCE + ")"));
		assertTrue(reference.contains("# GeoSPARQL functions and predicates reference"));
		assertTrue(reference.contains("FILTER(geof:sfWithin(?leftGeometry, ?rightGeometry))"));
		assertTrue(reference.contains("?left geo:sfWithin ?right ."));
		assertTrue(reference.contains("uses the GeoSPARQL index for candidate lookup followed by exact relation "
				+ "evaluation"));
	}

	@Test
	public void geofFunctionsAreAvailableWhenPluginIsDisabled() {
		assertAsk(PREFIXES
				+ "ASK {\n"
				+ "  FILTER(geof:sfWithin(\n"
				+ "    \"POLYGON((1 1,1 2,2 2,2 1,1 1))\"^^geo:wktLiteral,\n"
				+ "    \"POLYGON((0 0,0 4,4 4,4 0,0 0))\"^^geo:wktLiteral\n"
				+ "  ))\n"
				+ "}");
	}

	@Test
	public void documentedPropertyRelationPredicatesAreRegistered() throws IOException {
		Set<IRI> expected = Set.of(
				GeoConstants.GEO_SF_EQUALS,
				GeoConstants.GEO_SF_DISJOINT,
				GeoConstants.GEO_SF_INTERSECTS,
				GeoConstants.GEO_SF_TOUCHES,
				GeoConstants.GEO_SF_CROSSES,
				GeoConstants.GEO_SF_WITHIN,
				GeoConstants.GEO_SF_CONTAINS,
				GeoConstants.GEO_SF_OVERLAPS,
				GeoConstants.GEO_EH_EQUALS,
				GeoConstants.GEO_EH_DISJOINT,
				GeoConstants.GEO_EH_MEET,
				GeoConstants.GEO_EH_OVERLAP,
				GeoConstants.GEO_EH_COVERS,
				GeoConstants.GEO_EH_COVERED_BY,
				GeoConstants.GEO_EH_INSIDE,
				GeoConstants.GEO_EH_CONTAINS,
				GeoConstants.GEO_RCC8_EQ,
				GeoConstants.GEO_RCC8_DC,
				GeoConstants.GEO_RCC8_EC,
				GeoConstants.GEO_RCC8_PO,
				GeoConstants.GEO_RCC8_TPPI,
				GeoConstants.GEO_RCC8_TPP,
				GeoConstants.GEO_RCC8_NTPP,
				GeoConstants.GEO_RCC8_NTPPI);

		Set<IRI> actual = Arrays.stream(GeoSparqlPropertyRelation.values())
				.map(GeoSparqlPropertyRelation::getPredicateUri)
				.collect(Collectors.toSet());

		assertEquals(expected, actual);

		String reference = Files.readString(Path.of(FUNCTIONS_AND_PREDICATES_REFERENCE));
		for (IRI predicate : actual) {
			assertTrue("Missing documented predicate " + predicate,
					reference.contains("| `geo:" + predicate.getLocalName() + "` |"));
		}
		assertFalse(reference.contains("| `geo:relate` |"));
	}

	@Test
	public void indexedPropertyRelationsCoverSimpleFeaturesEgenhoferAndRcc8() {
		executeSparqlUpdateQuery(PREFIXES
				+ "INSERT DATA {\n"
				+ "  ex:outer a geo:Feature ;\n"
				+ "    geo:hasDefaultGeometry ex:outerGeom .\n"
				+ "  ex:outerGeom a geo:Geometry ;\n"
				+ "    geo:asWKT \"POLYGON((0 0,0 4,4 4,4 0,0 0))\"^^geo:wktLiteral .\n"
				+ "  ex:inner a geo:Feature ;\n"
				+ "    geo:hasDefaultGeometry ex:innerGeom .\n"
				+ "  ex:innerGeom a geo:Geometry ;\n"
				+ "    geo:asWKT \"POLYGON((1 1,1 2,2 2,2 1,1 1))\"^^geo:wktLiteral .\n"
				+ "}");

		enablePlugin();

		assertAsk(PREFIXES + "ASK { ex:inner geo:sfWithin ex:outer }");
		assertAsk(PREFIXES + "ASK { ex:inner geo:ehInside ex:outer }");
		assertAsk(PREFIXES + "ASK { ex:inner geo:rcc8ntpp ex:outer }");
		assertAsk(PREFIXES
				+ "ASK {\n"
				+ "  ex:innerGeom geo:sfWithin\n"
				+ "    \"POLYGON((0 0,0 4,4 4,4 0,0 0))\"^^geo:wktLiteral .\n"
				+ "}");
	}

	private void assertAsk(String query) {
		assertTrue(connection.prepareBooleanQuery(QueryLanguage.SPARQL, query).evaluate());
	}
}
