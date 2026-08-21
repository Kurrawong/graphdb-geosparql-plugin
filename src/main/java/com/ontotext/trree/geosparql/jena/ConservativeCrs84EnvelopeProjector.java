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
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

/**
 * Derives a CRS84 index envelope from a source geometry literal.
 *
 * <p>Candidate bounds for non-CRS84 geometries are derived by conservatively transforming the geometry's source-CRS
 * envelope using Apache SIS {@code Envelopes.transform(CoordinateOperation, Envelope)}. The transformed bounds are
 * used only for Lucene candidate lookup; exact evaluation continues to use the source geometry literal and its native
 * CRS. Apache SIS provides a curvature-aware conservative approximation rather than a formal guarantee for every
 * possible coordinate operation. The plugin therefore widens the transformed bounds where appropriate and falls back
 * to the world CRS84 envelope whenever the transformed result cannot be safely represented by the CRS84 Lucene
 * envelope model. Candidate-envelope optimisation may introduce false positives but must not introduce false
 * negatives. When safe candidate bounds cannot be established, the implementation uses the world CRS84 envelope and
 * relies on exact evaluation to determine the relation.
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
		CoordinateReferenceSystem sourceCrs = horizontalCrs(sourceWrapper.getCRS());
		CoordinateReferenceSystem targetCrs = SRSRegistry.getCRS(IndexGeometry.INDEX_CRS);
		GeneralEnvelope sourceEnvelope = createSourceEnvelope(sourceWrapper, sourceCrs);
		CoordinateOperation operation = CRS.findOperation(sourceCrs, targetCrs, null);
		org.opengis.geometry.Envelope transformed = Envelopes.transform(operation, sourceEnvelope);
		return toLuceneGeoEnvelope(transformed);
	}

	private static CoordinateReferenceSystem horizontalCrs(CoordinateReferenceSystem sourceCrs)
			throws TransformException {
		CoordinateReferenceSystem horizontal = CRS.getHorizontalComponent(sourceCrs);
		if (horizontal == null) {
			throw new TransformException("Source CRS has no two-dimensional horizontal component.");
		}
		CoordinateSystem coordinateSystem = horizontal.getCoordinateSystem();
		if (coordinateSystem == null || coordinateSystem.getDimension() != 2) {
			throw new TransformException("Source CRS horizontal component is not two-dimensional.");
		}
		return horizontal;
	}

	private static GeneralEnvelope createSourceEnvelope(GeometryWrapper sourceWrapper,
			CoordinateReferenceSystem sourceCrs) {
		Envelope sourceBounds = sourceWrapper.getParsingGeometry().getEnvelopeInternal();
		GeneralEnvelope sourceEnvelope = new GeneralEnvelope(sourceCrs);
		sourceEnvelope.setRange(0, sourceBounds.getMinX(), sourceBounds.getMaxX());
		sourceEnvelope.setRange(1, sourceBounds.getMinY(), sourceBounds.getMaxY());
		return sourceEnvelope;
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

	static Envelope worldCrs84Envelope() {
		Rectangle world = SpatialContext.GEO.getWorldBounds();
		return new Envelope(world.getMinX(), world.getMaxX(), world.getMinY(), world.getMaxY());
	}
}
