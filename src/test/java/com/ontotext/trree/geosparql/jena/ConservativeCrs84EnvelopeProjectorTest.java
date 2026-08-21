package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.GeoSparqlPropertyRelation;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.sis.geometry.GeneralEnvelope;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that index envelopes remain conservative candidate bounds for CRS84 lookup.
 */
public class ConservativeCrs84EnvelopeProjectorTest {
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String EPSG_4326 = "http://www.opengis.net/def/crs/EPSG/0/4326";
	private static final String PROJECTED_LINE_WKT =
			"<" + EPSG_32634 + "> LINESTRING(200000 7000000, 800000 7000000)";
	private static final String PROJECTED_POINT_WKT =
			"<" + EPSG_32634 + "> POINT(500000 7000000)";
	// Independently derived CRS84 coordinates for the EPSG:32634 line endpoints and midpoint.
	private static final double LINE_LEFT_LON = 15.070046;
	private static final double LINE_RIGHT_LON = 26.929954;
	private static final double LINE_ENDPOINT_LAT = 63.005028;
	private static final double LINE_MIDPOINT_LAT = 63.129340;
	private static final double CRS84_COORDINATE_TOLERANCE = 1e-4;
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
	public void projectedPointUsesALocalEnvelopeRatherThanTheWorldFallback() {
		IndexGeometry point = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt(
						"<" + EPSG_32634 + "> POINT(799997.80 4589779.63)"));

		assertTrue(point.isSpatialCandidate());
		assertFalse(worldCrs84Envelope().equals(point.indexEnvelope()));
		assertTrue(point.indexEnvelope().getWidth() < 1.0);
		assertTrue(point.indexEnvelope().getHeight() < 1.0);
	}

	@Test
	public void projectedLineEnvelopeContainsTheTransformedMidpointTheVertexEnvelopeOmits()
			throws Exception {
		IndexGeometry line = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt(PROJECTED_LINE_WKT));
		IndexGeometry midpoint = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt(PROJECTED_POINT_WKT));
		Envelope indexEnvelope = line.indexEnvelope();
		Envelope vertexEnvelope = vertexTransformedEnvelope(line.sourceGeometryLiteral());

		assertTrue(line.isSpatialCandidate());
		assertFalse(worldCrs84Envelope().equals(indexEnvelope));
		assertFalse(vertexEnvelope.intersects(midpoint.indexEnvelope()));
		assertTrue(indexEnvelope.intersects(midpoint.indexEnvelope()));
		assertTrue(indexEnvelope.getMaxY() > vertexEnvelope.getMaxY());
		assertTrue(indexEnvelope.getMinX() <= LINE_LEFT_LON + CRS84_COORDINATE_TOLERANCE);
		assertTrue(indexEnvelope.getMaxX() >= LINE_RIGHT_LON - CRS84_COORDINATE_TOLERANCE);
		assertTrue(indexEnvelope.getMaxY() >= LINE_MIDPOINT_LAT - CRS84_COORDINATE_TOLERANCE);
		assertTrue(indexEnvelope.getMinY() <= LINE_ENDPOINT_LAT + CRS84_COORDINATE_TOLERANCE);
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
	public void nonCrs84GeographicCollectionUsesAxisOrderEnvelopeRatherThanWorld() {
		IndexGeometry envelope = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt(
						"<" + EPSG_4326 + "> GEOMETRYCOLLECTION(POINT(50 10),POINT(51 11))"));

		assertTrue(envelope.isSpatialCandidate());
		assertFalse(worldCrs84Envelope().equals(envelope.indexEnvelope()));
		assertTrue(envelope.indexEnvelope().getMinX() <= 10.0);
		assertTrue(envelope.indexEnvelope().getMaxX() >= 11.0);
		assertTrue(envelope.indexEnvelope().getMinY() <= 50.0);
		assertTrue(envelope.indexEnvelope().getMaxY() >= 51.0);
		assertTrue(envelope.indexEnvelope().getWidth() < 5.0);
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

	@Test
	public void wraparoundAndOutOfRangeEnvelopesUseTheWorldFallback() {
		assertEquals(worldCrs84Envelope(),
				ConservativeCrs84EnvelopeProjector.toLuceneGeoEnvelope(range(170, -170, 10, 20)));
		assertEquals(worldCrs84Envelope(),
				ConservativeCrs84EnvelopeProjector.toLuceneGeoEnvelope(range(170, 190, 10, 20)));
		assertEquals(worldCrs84Envelope(),
				ConservativeCrs84EnvelopeProjector.toLuceneGeoEnvelope(range(10, 20, -100, 80)));
	}

	@Test
	public void envelopeOnTheGeographicRimStaysInsideLuceneGeoBounds() {
		Envelope envelope = ConservativeCrs84EnvelopeProjector.toLuceneGeoEnvelope(
				range(180.0, 180.0, 90.0, 90.0));

		assertTrue(envelope.getMinX() >= -180.0);
		assertTrue(envelope.getMaxX() <= 180.0);
		assertTrue(envelope.getMinY() >= -90.0);
		assertTrue(envelope.getMaxY() <= 90.0);
		assertFalse(envelope.isNull());
	}

	private static GeneralEnvelope range(double minX, double maxX, double minY, double maxY) {
		GeneralEnvelope envelope = new GeneralEnvelope(2);
		envelope.setRange(0, minX, maxX);
		envelope.setRange(1, minY, maxY);
		return envelope;
	}

	private static Envelope vertexTransformedEnvelope(SourceGeometryLiteral source) throws Exception {
		GeometryWrapper transformed = source.asGeometryWrapper().transform(IndexGeometry.INDEX_CRS);
		return transformed.getXYGeometry().getEnvelopeInternal();
	}

	private static Envelope worldCrs84Envelope() {
		return new Envelope(-180.0, 180.0, -90.0, 90.0);
	}
}
