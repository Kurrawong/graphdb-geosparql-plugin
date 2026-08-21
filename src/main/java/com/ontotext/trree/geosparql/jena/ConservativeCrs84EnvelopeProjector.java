package com.ontotext.trree.geosparql.jena;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.registry.SRSRegistry;
import org.apache.sis.geometry.Envelopes;
import org.apache.sis.geometry.GeneralEnvelope;
import org.apache.sis.referencing.CRS;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

/**
 * Derives a CRS84 index envelope from a source geometry literal.
 *
 * <p>Native CRS84 sources use their source envelope. Other non-empty sources transform the source-CRS bounding box
 * with Apache SIS {@code Envelopes.transform}. SIS documents that this transformation is only approximated: the
 * returned envelope may be bigger than the smallest possible bounding box, but should not be smaller in most cases.
 *
 * <p>If that result cannot be stored as a single Lucene {@code SpatialContext.GEO} rectangle, including wraparound
 * and bounds that extend outside the geographic world, the world CRS84 envelope is used instead. The returned
 * envelope may be larger than the complete source geometry transformed to CRS84.
 */
final class ConservativeCrs84EnvelopeProjector {
	Envelope project(SourceGeometryLiteral source) {
		GeometryWrapper sourceWrapper = source.asGeometryWrapper();
		if (sourceWrapper.isEmpty()) {
			return new Envelope();
		}
		if (IndexGeometry.INDEX_CRS.equals(sourceWrapper.getSrsURI())) {
			return new Envelope(sourceWrapper.getXYGeometry().getEnvelopeInternal());
		}
		try {
			return transformSourceBounds(sourceWrapper);
		} catch (FactoryException | TransformException | RuntimeException ignored) {
			return worldCrs84Envelope();
		}
	}

	private static Envelope transformSourceBounds(GeometryWrapper sourceWrapper)
			throws FactoryException, TransformException {
		CoordinateReferenceSystem sourceCrs = sourceWrapper.getCRS();
		CoordinateReferenceSystem targetCrs = SRSRegistry.getCRS(IndexGeometry.INDEX_CRS);
		Envelope sourceBounds = sourceWrapper.getParsingGeometry().getEnvelopeInternal();
		GeneralEnvelope sourceEnvelope = new GeneralEnvelope(sourceCrs);
		sourceEnvelope.setRange(0, sourceBounds.getMinX(), sourceBounds.getMaxX());
		sourceEnvelope.setRange(1, sourceBounds.getMinY(), sourceBounds.getMaxY());
		CoordinateOperation operation = CRS.findOperation(sourceCrs, targetCrs, null);
		org.opengis.geometry.Envelope transformed = Envelopes.transform(operation, sourceEnvelope);
		return toLuceneGeoEnvelope(transformed);
	}

	static Envelope toLuceneGeoEnvelope(org.opengis.geometry.Envelope transformed) {
		if (transformed == null || transformed.getDimension() < 2) {
			return worldCrs84Envelope();
		}
		double minX = transformed.getMinimum(0);
		double maxX = transformed.getMaximum(0);
		double minY = transformed.getMinimum(1);
		double maxY = transformed.getMaximum(1);
		Rectangle world = SpatialContext.GEO.getWorldBounds();
		if (!Double.isFinite(minX) || !Double.isFinite(maxX)
				|| !Double.isFinite(minY) || !Double.isFinite(maxY)
				|| minX > maxX || minY > maxY
				|| minX < world.getMinX() || maxX > world.getMaxX()
				|| minY < world.getMinY() || maxY > world.getMaxY()) {
			return worldCrs84Envelope();
		}
		minX = Math.max(world.getMinX(), Math.nextDown(minX));
		maxX = Math.min(world.getMaxX(), Math.nextUp(maxX));
		minY = Math.max(world.getMinY(), Math.nextDown(minY));
		maxY = Math.min(world.getMaxY(), Math.nextUp(maxY));
		if (minX > maxX || minY > maxY) {
			return worldCrs84Envelope();
		}
		return new Envelope(minX, maxX, minY, maxY);
	}

	private static Envelope worldCrs84Envelope() {
		Rectangle world = SpatialContext.GEO.getWorldBounds();
		return new Envelope(world.getMinX(), world.getMaxX(), world.getMinY(), world.getMaxY());
	}
}
