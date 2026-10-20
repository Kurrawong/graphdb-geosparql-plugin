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
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Verifies serialized spatial aggregate results for empty geometries, datatypes, and coordinate layouts. */
public class SpatialAggregateFactoryTest {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final String EPSG_4326 = "<http://www.opengis.net/def/crs/EPSG/0/4326> ";

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
	public void collectKeepsZAndMOrdinatesOfLatitudeFirstGeometries() {
		Literal z = collect(List.of(wkt(EPSG_4326 + "POINT Z(1 2 5)"), wkt(EPSG_4326 + "POINT Z(3 4 7)")));
		Literal m = collect(List.of(wkt(EPSG_4326 + "POINT M(1 2 5)"), wkt(EPSG_4326 + "POINT M(3 4 7)")));

		assertEquals(EPSG_4326 + "GEOMETRYCOLLECTION Z(POINT Z(1 2 5), POINT Z(3 4 7))", z.stringValue());
		assertEquals(EPSG_4326 + "GEOMETRYCOLLECTION M(POINT M(1 2 5), POINT M(3 4 7))", m.stringValue());
		Geometry reparsedZ = parsingGeometry(z);
		Geometry reparsedM = parsingGeometry(m);
		assertEquals(5.0, reparsedZ.getGeometryN(0).getCoordinate().getZ(), 0.0);
		assertEquals(7.0, reparsedZ.getGeometryN(1).getCoordinate().getZ(), 0.0);
		assertEquals(5.0, reparsedM.getGeometryN(0).getCoordinate().getM(), 0.0);
		assertEquals(7.0, reparsedM.getGeometryN(1).getCoordinate().getM(), 0.0);
	}

	@Test
	public void mixedCoordinateLayoutsAreReducedToSharedOrdinatesInEitherInputOrder() throws Exception {
		Literal xyz = wkt("POINT Z(1 2 5)");
		Literal xym = wkt("POINT M(0 5 9)");
		Literal xy = wkt("POINT(3 4)");
		Map<List<Literal>, String> collections = Map.of(
				List.of(xyz, xym, xy), "GEOMETRYCOLLECTION(POINT(1 2), POINT(0 5), POINT(3 4))",
				List.of(xy, xym, xyz), "GEOMETRYCOLLECTION(POINT(3 4), POINT(0 5), POINT(1 2))");

		for (Map.Entry<List<Literal>, String> entry : collections.entrySet()) {
			List<Literal> group = entry.getKey();
			Literal box = (Literal) aggregate(GeoConstants.GEOF_AGG_BOUNDING_BOX.stringValue(), group);
			Literal hull = (Literal) aggregate(GeoConstants.GEOF_AGG_CONVEX_HULL.stringValue(), group);

			assertEquals(entry.getValue(), collect(group).stringValue());
			assertNotNull(entry.getValue(), box);
			assertNotNull(entry.getValue(), hull);
			assertEquals("POLYGON((0 2, 0 5, 3 5, 3 2, 0 2))", box.stringValue());
			assertEquals("POLYGON((1 2, 0 5, 3 4, 1 2))", hull.stringValue());
		}
	}

	@Test
	public void unionKeepsZOrLeavesTheGroupUnboundLikeScalarUnion() {
		String union = GeoConstants.GEOF_AGG_UNION.stringValue();

		assertEquals("MULTIPOINT Z((1 2 5), (3 4 7))",
				aggregate(union, List.of(wkt("POINT Z(1 2 5)"), wkt("POINT Z(3 4 7)"))).stringValue());
		assertNull(aggregate(union, List.of(wkt(EPSG_4326 + "POINT Z(1 2 5)"), wkt(EPSG_4326 + "POINT Z(3 4 7)"))));
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

	private static Geometry parsingGeometry(Literal literal) {
		return JenaGeometryAdapter.toSourceGeometryLiteral(literal).asGeometryWrapper().getParsingGeometry();
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
