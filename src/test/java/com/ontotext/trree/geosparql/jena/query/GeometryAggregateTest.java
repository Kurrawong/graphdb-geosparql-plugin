package com.ontotext.trree.geosparql.jena.query;

import com.ontotext.trree.geosparql.jena.JenaGeoSparqlException;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.GMLDatatype;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.junit.Test;
import org.locationtech.jts.io.WKTReader;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GeometryAggregateTest {
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";

	@Test
	public void collectKeepsSharedSrsAndDatatype() throws Exception {
		GeometryWrapper collected = GeometryAggregate.collect(List.of(
				wkt("<" + EPSG_32634 + "> POINT(500000 4600000)"),
				wkt("<" + EPSG_32634 + "> LINESTRING(500000 4600000,500010 4600010)")));

		assertEquals(EPSG_32634, collected.getSrsURI());
		assertEquals(WKTDatatype.URI, collected.getGeometryDatatypeURI());
		assertTrue(collected.getXYGeometry().equalsExact(new WKTReader().read(
				"GEOMETRYCOLLECTION(POINT(500000 4600000),LINESTRING(500000 4600000,500010 4600010))")));
	}

	@Test
	public void mixedSrsIsTransformedToCrs84AndMixedDatatypesProduceWkt() throws Exception {
		GeometryWrapper collected = GeometryAggregate.collect(List.of(
				wkt("<http://www.opengis.net/def/crs/EPSG/0/4326> POINT(10 20)"),
				GeometryWrapper.extract("<gml:Point xmlns:gml='http://www.opengis.net/gml/3.2' "
						+ "srsName='" + SRS_URI.DEFAULT_WKT_CRS84 + "'><gml:pos>30 40</gml:pos></gml:Point>",
						GMLDatatype.URI)));

		assertEquals(SRS_URI.DEFAULT_WKT_CRS84, collected.getSrsURI());
		assertEquals(WKTDatatype.URI, collected.getGeometryDatatypeURI());
		assertTrue(collected.getXYGeometry().toText(), collected.getXYGeometry().equalsExact(
				new WKTReader().read("GEOMETRYCOLLECTION(POINT(20 10),POINT(30 40))"), 1e-9));
	}

	@Test
	public void boundingBoxEnclosesEveryGeometry() throws Exception {
		GeometryWrapper box = GeometryAggregate.boundingBox(List.of(wkt("POINT(0 0)"), wkt("LINESTRING(4 1,10 5)")));

		assertTrue(box.getXYGeometry().toText(), box.getXYGeometry().equalsTopo(
				new WKTReader().read("POLYGON((0 0,10 0,10 5,0 5,0 0))")));
	}

	@Test
	public void convexHullSpansEveryGeometry() throws Exception {
		GeometryWrapper hull = GeometryAggregate.convexHull(List.of(wkt("POINT(0 0)"), wkt("POINT(2 2)"),
				wkt("LINESTRING(10 0,0 10)")));

		assertTrue(hull.getXYGeometry().toText(), hull.getXYGeometry().equalsTopo(
				new WKTReader().read("POLYGON((0 0,10 0,0 10,0 0))")));
	}

	@Test
	public void unionDissolvesOverlappingGeometries() throws Exception {
		GeometryWrapper union = GeometryAggregate.union(List.of(wkt("POLYGON((0 0,2 0,2 2,0 2,0 0))"),
				wkt("POLYGON((1 0,3 0,3 2,1 2,1 0))"), wkt("POINT(1 1)")));

		assertTrue(union.getXYGeometry().toText(), union.getXYGeometry().equalsTopo(
				new WKTReader().read("POLYGON((0 0,3 0,3 2,0 2,0 0))")));
		assertEquals("Polygon", union.getGeometryType());
	}

	@Test
	public void centroidIsTheCentroidOfTheCollectedGeometries() {
		GeometryWrapper centroid = GeometryAggregate.centroid(List.of(wkt("POINT(0 0)"), wkt("POINT(10 0)"),
				wkt("POINT(5 9)")));

		assertEquals("Point", centroid.getGeometryType());
		assertEquals(5.0, centroid.getXYGeometry().getCoordinate().x, 1e-12);
		assertEquals(3.0, centroid.getXYGeometry().getCoordinate().y, 1e-12);
	}

	@Test
	public void boundingCircleCoversEveryGeometry() throws Exception {
		GeometryWrapper circle = GeometryAggregate.boundingCircle(List.of(wkt("POINT(0 0)"), wkt("POINT(10 0)")));

		assertEquals("Polygon", circle.getGeometryType());
		assertTrue(circle.getXYGeometry().covers(new WKTReader().read("MULTIPOINT((0 0),(10 0))")));
		assertEquals(5.0, circle.getXYGeometry().getCentroid().getX(), 1e-9);
		assertEquals(0.0, circle.getXYGeometry().getCentroid().getY(), 1e-9);
	}

	@Test
	public void concaveHullTargetPercentRangesFromTightestHullToConvexHull() throws Exception {
		List<GeometryWrapper> uShape = List.of(wkt("MULTIPOINT((0 0),(5 0),(10 0),(10 5),(10 10),(8 10),(8 5),(8 2),"
				+ "(5 2),(2 2),(2 5),(2 10),(0 10),(0 5))"));

		GeometryWrapper tightest = GeometryAggregate.concaveHull(uShape, 0.0);
		GeometryWrapper convex = GeometryAggregate.concaveHull(uShape, 1.0);

		assertTrue(convex.getXYGeometry().toText(), convex.getXYGeometry().equalsTopo(
				new WKTReader().read("POLYGON((0 0,10 0,10 10,0 10,0 0))")));
		assertTrue(tightest.getXYGeometry().toText(), tightest.getXYGeometry().getArea() < 100.0);
		assertTrue(tightest.getXYGeometry().covers(uShape.get(0).getXYGeometry()));
	}

	@Test
	public void concaveHullRejectsTargetPercentOutsideZeroToOne() {
		List<GeometryWrapper> points = List.of(wkt("POINT(0 0)"), wkt("POINT(1 1)"), wkt("POINT(0 1)"));
		for (double targetPercent : new double[] {-0.1, 1.1, Double.NaN}) {
			assertThrows(String.valueOf(targetPercent), JenaGeoSparqlException.class,
					() -> GeometryAggregate.concaveHull(points, targetPercent));
		}
	}

	@Test
	public void groupOfEmptyGeometriesProducesEmptyResults() {
		List<GeometryWrapper> empties = List.of(wkt("POINT EMPTY"), wkt("POLYGON EMPTY"));

		assertTrue(GeometryAggregate.collect(empties).isEmpty());
		assertTrue(GeometryAggregate.union(empties).isEmpty());
		assertTrue(GeometryAggregate.centroid(empties).isEmpty());
	}

	private static GeometryWrapper wkt(String lexicalForm) {
		return GeometryWrapper.extract(lexicalForm, WKTDatatype.URI);
	}
}
