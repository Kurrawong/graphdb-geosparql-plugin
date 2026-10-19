package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies conversion to reusable CRS84 KML geometry literals. */
public class AsKmlFunctionTest {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();

	@Test
	public void wktPointConvertsToNamespacedKmlPoint() throws Exception {
		Literal result = evaluate(wkt("POINT(-83.38 33.95)"));

		assertEquals(GeoConstants.GEO_KML_LITERAL, result.getDatatype());
		assertEquals("<Point xmlns=\"http://www.opengis.net/kml/2.2\"><coordinates>-83.38,33.95</coordinates></Point>",
				compact(result));
	}

	@Test
	public void emptySourcesReturnZeroLengthKmlLiteral() throws Exception {
		for (String empty : new String[] {"POINT EMPTY", "POLYGON EMPTY", "GEOMETRYCOLLECTION EMPTY"}) {
			Literal result = evaluate(wkt(empty));

			assertEquals(empty, GeoConstants.GEO_KML_LITERAL, result.getDatatype());
			assertEquals(empty, "", result.stringValue());
		}
	}

	@Test
	public void nestedCollectionsAreFlattenedAndEmptyMembersOmittedSoOutputReadsBack() throws Exception {
		Map<String, String> cases = new LinkedHashMap<>();
		cases.put("MULTIPOINT(EMPTY,(1 2),(3 4))",
				"<MultiGeometry xmlns=\"http://www.opengis.net/kml/2.2\">"
						+ "<Point><coordinates>1,2</coordinates></Point>"
						+ "<Point><coordinates>3,4</coordinates></Point></MultiGeometry>");
		cases.put("GEOMETRYCOLLECTION(POINT EMPTY,POINT(1 2),MULTILINESTRING(EMPTY,(1 2,3 4)))",
				"<MultiGeometry xmlns=\"http://www.opengis.net/kml/2.2\">"
						+ "<Point><coordinates>1,2</coordinates></Point>"
						+ "<LineString><coordinates>1,2 3,4</coordinates></LineString></MultiGeometry>");

		JenaGeometryAdapter.initialize();
		for (Map.Entry<String, String> entry : cases.entrySet()) {
			GeometryWrapper wrapper = new GeometryWrapper(new WKTReader().read(entry.getKey()),
					SRS_URI.DEFAULT_WKT_CRS84, GeoConstants.GEO_WKT_LITERAL.stringValue());

			Literal result = JenaGeometryAdapter.toKmlLiteral(VALUE_FACTORY, wrapper, 2);

			assertEquals(entry.getKey(), entry.getValue(), compact(result));
			assertFalse(entry.getKey(),
					JenaGeometryAdapter.toSourceGeometryLiteral(result).asGeometryWrapper().isEmpty());
		}
	}

	@Test
	public void ordinatesAreWrittenWithoutExponentNotation() throws Exception {
		Literal result = evaluate(wkt("POINT(0.00001 -0.000001)"));

		assertEquals("<Point xmlns=\"http://www.opengis.net/kml/2.2\"><coordinates>0.00001,-0.000001</coordinates></Point>",
				compact(result));
	}

	@Test
	public void conversionFromOtherSerializationsIsTwoDimensional() throws Exception {
		Literal result = evaluate(wkt("POINT Z(1 2 3)"));

		assertEquals("<Point xmlns=\"http://www.opengis.net/kml/2.2\"><coordinates>1,2</coordinates></Point>",
				compact(result));
	}

	@Test
	public void nativeKmlIdentityPreservesAltitude() throws Exception {
		Literal result = evaluate(VALUE_FACTORY.createLiteral(
				"<LineString><coordinates>1,2,10 3,4,20</coordinates></LineString>", GeoConstants.GEO_KML_LITERAL));

		assertEquals("<LineString xmlns=\"http://www.opengis.net/kml/2.2\"><coordinates>1,2,10 3,4,20</coordinates>"
				+ "</LineString>", compact(result));
	}

	@Test
	public void sourceCrsIsConvertedToCrs84LongitudeLatitude() throws Exception {
		Literal result = evaluate(wkt("<http://www.opengis.net/def/crs/EPSG/0/4326> POINT(33.95 -83.38)"));

		assertEquals("<Point xmlns=\"http://www.opengis.net/kml/2.2\"><coordinates>-83.38,33.95</coordinates></Point>",
				compact(result));
	}

	@Test
	public void polygonWithHoleReadsBackAsEqualGeometry() throws Exception {
		Literal source = wkt("POLYGON((0 0,10 0,10 10,0 10,0 0),(2 2,2 4,4 4,4 2,2 2))");

		Literal result = evaluate(source);

		Geometry expected = JenaGeometryAdapter.toSourceGeometryLiteral(source).asGeometryWrapper().getXYGeometry();
		Geometry parsed = JenaGeometryAdapter.toSourceGeometryLiteral(result).asGeometryWrapper().getXYGeometry();
		assertTrue(expected.equalsTopo(parsed));
	}

	private static Literal evaluate(Literal source) throws Exception {
		return (Literal) JenaFunctionEvaluator.evaluate(VALUE_FACTORY, GeoConstants.GEOF_AS_KML.stringValue(), source);
	}

	private static Literal wkt(String lexicalForm) {
		return VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL);
	}

	private static String compact(Literal literal) {
		return literal.stringValue().replaceAll(">\\s+<", "><").trim();
	}
}
