package com.ontotext.trree.geosparql.jena;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

/**
 * Derives a CRS84 index envelope that may be larger than the complete transformed source geometry, but never smaller.
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
		return worldCrs84Envelope();
	}

	private static Envelope worldCrs84Envelope() {
		Rectangle world = SpatialContext.GEO.getWorldBounds();
		return new Envelope(world.getMinX(), world.getMaxX(), world.getMinY(), world.getMaxY());
	}
}
