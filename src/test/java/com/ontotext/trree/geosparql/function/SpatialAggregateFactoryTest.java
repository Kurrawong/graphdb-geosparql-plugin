package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryBindingSet;
import org.eclipse.rdf4j.query.parser.sparql.aggregate.AggregateCollector;
import org.eclipse.rdf4j.query.parser.sparql.aggregate.AggregateFunction;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Verifies serialized spatial aggregate results for groups with empty geometries. */
public class SpatialAggregateFactoryTest {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();

	@Test
	public void collectOmitsEmptyMembersInEitherInputOrder() throws Exception {
		Literal empty = wkt("POINT EMPTY");
		Literal point = wkt("POINT(3 4)");

		for (List<Literal> group : List.of(List.of(empty, point), List.of(point, empty))) {
			Literal result = collect(group);

			assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
			assertTrue(result.stringValue(), geometry(result).equalsExact(
					new WKTReader().read("GEOMETRYCOLLECTION(POINT(3 4))")));
		}
	}

	@Test
	public void collectOfOnlyEmptyGeometriesIsEmptyGeometryCollection() {
		Literal result = collect(List.of(wkt("POINT EMPTY"), wkt("POLYGON EMPTY")));

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertEquals("GEOMETRYCOLLECTION EMPTY", result.stringValue());
	}

	@Test
	public void collectPrunesNestedEmptyMembersAndKeepsNesting() throws Exception {
		Literal nested = VALUE_FACTORY.createLiteral("{\"type\":\"GeometryCollection\",\"geometries\":["
				+ "{\"type\":\"Point\",\"coordinates\":[]},{\"type\":\"Point\",\"coordinates\":[3,4]}]}",
				GeoConstants.GEO_JSON_LITERAL);

		Literal result = collect(List.of(nested, wkt("POINT(1 2)")));

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertTrue(result.stringValue(), geometry(result).equalsExact(
				new WKTReader().read("GEOMETRYCOLLECTION(GEOMETRYCOLLECTION(POINT(3 4)),POINT(1 2))")));
	}

	@Test
	public void collectPrunesEmptyMembersBelowCollectionsWhoseMemberCountIsUnchanged() throws Exception {
		Literal nested = VALUE_FACTORY.createLiteral("{\"type\":\"GeometryCollection\",\"geometries\":["
				+ "{\"type\":\"GeometryCollection\",\"geometries\":["
				+ "{\"type\":\"Point\",\"coordinates\":[]},{\"type\":\"Point\",\"coordinates\":[3,4]}]}]}",
				GeoConstants.GEO_JSON_LITERAL);

		Literal result = collect(List.of(nested, wkt("POINT(1 2)")));

		assertEquals(GeoConstants.GEO_WKT_LITERAL, result.getDatatype());
		assertTrue(result.stringValue(), geometry(result).equalsExact(new WKTReader().read(
				"GEOMETRYCOLLECTION(GEOMETRYCOLLECTION(GEOMETRYCOLLECTION(POINT(3 4))),POINT(1 2))")));
	}

	@Test
	public void groupOfLegacyWktLiteralsKeepsTheLegacyDatatype() throws Exception {
		Literal result = collect(List.of(legacyWkt("POINT(1 2)"), legacyWkt("POINT(3 4)")));

		assertEquals(GeoConstants.XMLSCHEMA_SPATIAL_TEXT, result.getDatatype());
		assertTrue(result.stringValue(), geometry(result).equalsExact(
				new WKTReader().read("GEOMETRYCOLLECTION(POINT(1 2),POINT(3 4))")));
	}

	@Test
	public void legacyAndGeoSparqlWktLiteralsProduceGeoSparqlWktLiteral() {
		for (List<Literal> group : List.of(List.of(legacyWkt("POINT(1 2)"), wkt("POINT(3 4)")),
				List.of(wkt("POINT(3 4)"), legacyWkt("POINT(1 2)")))) {
			assertEquals(GeoConstants.GEO_WKT_LITERAL, collect(group).getDatatype());
		}
	}

	@Test
	public void groupWithoutGeometriesIsUnbound() {
		assertNull(aggregate(GeoConstants.GEOF_AGG_COLLECT.stringValue(), List.of()));
	}

	private static Literal collect(List<Literal> group) {
		return (Literal) aggregate(GeoConstants.GEOF_AGG_COLLECT.stringValue(), group);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Value aggregate(String iri, List<Literal> group) {
		SpatialAggregateFactory factory = SpatialAggregateFactory.all().stream()
				.filter(candidate -> candidate.getIri().equals(iri))
				.findFirst()
				.orElseThrow();
		AggregateCollector collector = factory.getCollector();
		AggregateFunction function = factory.buildFunction(
				bindings -> group.get(Integer.parseInt(bindings.getValue("index").stringValue())));
		for (int index = 0; index < group.size(); index++) {
			QueryBindingSet bindings = new QueryBindingSet();
			bindings.addBinding("index", VALUE_FACTORY.createLiteral(index));
			function.processAggregate(bindings, value -> true, collector);
		}
		return collector.getFinalValue();
	}

	private static Geometry geometry(Literal literal) {
		return JenaGeometryAdapter.toSourceGeometryLiteral(literal).asGeometryWrapper().getXYGeometry();
	}

	private static Literal wkt(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL);
	}

	private static Literal legacyWkt(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.XMLSCHEMA_SPATIAL_TEXT);
	}
}
