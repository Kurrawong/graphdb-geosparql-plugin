package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.GeoSparqlPropertyRelation;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that index envelopes are never smaller than the complete CRS84-transformed source geometry.
 */
public class ConservativeCrs84EnvelopeProjectorTest {
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String PROJECTED_LINE_WKT =
			"<" + EPSG_32634 + "> LINESTRING(200000 7000000, 800000 7000000)";
	private static final String PROJECTED_POINT_WKT =
			"<" + EPSG_32634 + "> POINT(500000 7000000)";
	private static final ConservativeCrs84EnvelopeProjector PROJECTOR =
			new ConservativeCrs84EnvelopeProjector();

	@Before
	public void initializeAdapter() {
		JenaGeometryAdapter.initialize();
	}

	@Test
	public void emptyGeometryProducesNullEnvelopeAndRemainsNonSpatial() {
		IndexGeometry sentinel = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("LINESTRING EMPTY"));

		assertTrue(sentinel.indexEnvelope().isNull());
		assertFalse(sentinel.isSpatialCandidate());
		assertTrue(PROJECTOR.project(sentinel.sourceGeometryLiteral()).isNull());
	}

	@Test
	public void crs84GeometryUsesItsSourceEnvelopeRatherThanTheWorldFallback() {
		SourceGeometryLiteral source = SourceGeometryLiteral.fromWkt(
				"LINESTRING(1 2,5 6)");
		IndexGeometry index = IndexGeometry.fromSourceGeometryLiteral(source);

		assertEquals(new Envelope(1.0, 5.0, 2.0, 6.0), PROJECTOR.project(source));
		assertEquals(new Envelope(1.0, 5.0, 2.0, 6.0), index.indexEnvelope());
		assertTrue(index.isSpatialCandidate());
		assertFalse(worldCrs84Envelope().equals(index.indexEnvelope()));
	}

	@Test
	public void nonEmptyProjectedPointUsesTheWorldEnvelope() {
		assertWorldEnvelope(IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt(
						"<" + EPSG_32634 + "> POINT(799997.80 4589779.63)")));
	}

	@Test
	public void nonEmptyProjectedLineUsesTheWorldEnvelope() {
		assertWorldEnvelope(IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt(PROJECTED_LINE_WKT)));
	}

	@Test
	public void projectedGeometriesCannotBeProvedDisjointFromTransformedVertexEnvelopes() {
		IndexGeometry line = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt(PROJECTED_LINE_WKT));
		IndexGeometry point = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt(PROJECTED_POINT_WKT));

		assertTrue(GeoSparqlPropertyRelation.SF_INTERSECTS.evaluate(
				line.sourceGeometryLiteral(), point.sourceGeometryLiteral()));
		assertTrue(line.indexEnvelope().intersects(point.indexEnvelope()));
		assertFalse(GeoSparqlPropertyRelation.SF_DISJOINT.evaluate(
				line.sourceGeometryLiteral(), point.sourceGeometryLiteral()));
		assertFalse(GeoSparqlPropertyRelation.EH_DISJOINT.evaluate(
				line.sourceGeometryLiteral(), point.sourceGeometryLiteral()));
		assertFalse(line.isEnvelopeCoveringRectangle());
		assertFalse(point.isEnvelopeCoveringRectangle());
	}

	@Test
	public void crs84RectangleContainmentAndSelectivityRemainIntact() {
		IndexGeometry rectangle = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("POLYGON((0 0,0 10,10 10,10 0,0 0))"));
		IndexGeometry inside = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("POINT(5 5)"));
		IndexGeometry outside = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("POINT(20 20)"));

		assertEquals(new Envelope(0.0, 10.0, 0.0, 10.0), rectangle.indexEnvelope());
		assertTrue(rectangle.isEnvelopeCoveringRectangle());
		assertTrue(rectangle.indexEnvelope().intersects(inside.indexEnvelope()));
		assertFalse(rectangle.indexEnvelope().intersects(outside.indexEnvelope()));
	}

	@Test
	public void worldEnvelopeStaysWithinLuceneGeoSpatialContextBounds() {
		Rectangle world = SpatialContext.GEO.getWorldBounds();
		assertEquals(-180.0, world.getMinX(), 0.0);
		assertEquals(180.0, world.getMaxX(), 0.0);
		assertEquals(-90.0, world.getMinY(), 0.0);
		assertEquals(90.0, world.getMaxY(), 0.0);
	}

	private static void assertWorldEnvelope(IndexGeometry geometry) {
		Envelope expected = worldCrs84Envelope();
		assertEquals(expected, PROJECTOR.project(geometry.sourceGeometryLiteral()));
		assertEquals(expected, geometry.indexEnvelope());
		assertTrue(geometry.isSpatialCandidate());
	}

	private static Envelope worldCrs84Envelope() {
		return new Envelope(-180.0, 180.0, -90.0, 90.0);
	}
}
