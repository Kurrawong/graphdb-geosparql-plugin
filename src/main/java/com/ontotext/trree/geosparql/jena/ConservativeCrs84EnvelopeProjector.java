package com.ontotext.trree.geosparql.jena;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.registry.SRSRegistry;
import org.apache.sis.geometry.Envelopes;
import org.apache.sis.geometry.GeneralEnvelope;
import org.apache.sis.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
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
 * <p>Native CRS84 sources use their source envelope. Empty sources yield a null envelope. Other non-empty sources
 * transform the source-CRS bounding box with Apache SIS {@code Envelopes.transform(CoordinateOperation, Envelope)}.
 * Sources with a three-dimensional CRS retain their vertical range through a full operation to WGS 84 3D before
 * reduction to CRS84. Other sources use an operation on their two-dimensional horizontal CRS. The result is used
 * only for Lucene candidate lookup. Exact evaluation uses the source geometry literal and its native CRS.
 *
 * <p>The plugin uses the {@code CoordinateOperation} overload because it samples envelope corners, edge midpoints
 * and center, uses transform derivatives to locate cubic-curve extrema, and accounts for poles and wraparound.
 * The {@code MathTransform} overload is not used: SIS documents that it can under-cover poles and handles longitude
 * wraparound only when wraparound steps are already in the chain. {@code CRS.findOperation} does not insert those
 * steps.
 *
 * <p>SIS documents the {@code CoordinateOperation} result as a curvature-aware approximation that may be larger than
 * the complete CRS84 image and should not be smaller in most cases. That wording is not a coverage proof. The
 * remaining residual is cubic-curve location on nonlinear inverse projections. SIS 1.6 does not identify a
 * detectably unsafe {@code CoordinateOperation} class among the two-dimensional EPSG-to-CRS84 operations this plugin
 * constructs, including GDA2020/MGA, UTM, Web Mercator, Lambert conformal conic, polar stereographic, and datum
 * shifts. Those sources keep a selective transformed envelope. Non-CRS84 geometries are not mapped to the world
 * envelope. Jacobian failure at an interior point is not treated as an unsafe operation class: pole and wraparound
 * handling of the {@code CoordinateOperation} overload still apply after sampling.
 *
 * <p>Antimeridian wraparound may broaden the candidate envelope, including to full longitude while keeping local
 * latitude, when that is what SIS reports through {@code getMinimum}/{@code getMaximum}. The world CRS84 envelope is
 * used only when the result still cannot be stored as one Lucene geographic rectangle: inverted lower/upper ordering,
 * non-finite ordinates, bounds outside the geographic world, missing two-dimensional horizontal CRS, or transform
 * failure. Unit-in-the-last-place widening does not close a geographically large miss.
 *
 * <p>Candidate lookup may include false positives. It must avoid false negatives. The plugin uses SIS's
 * curvature-aware {@code CoordinateOperation} envelope transformation for representable results and falls back to the
 * world envelope for transformation failures or results that cannot be represented as a single Lucene geographic
 * rectangle. SIS does not provide a formal coverage guarantee for finite transformed envelopes.
 */
final class ConservativeCrs84EnvelopeProjector {
	private static final String WGS84_3D = "http://www.opengis.net/def/crs/EPSG/0/4979";
	static final String FALLBACK_MISSING_HORIZONTAL_CRS = "missing-horizontal-crs";
	static final String FALLBACK_TRANSFORM_FAILURE = "transform-failure";
	static final String FALLBACK_UNREPRESENTABLE_RECTANGLE = "unrepresentable-rectangle";

	Envelope project(SourceGeometryLiteral source) {
		return projectBounds(source).envelope();
	}

	ProjectedCandidateBounds projectBounds(SourceGeometryLiteral source) {
		GeometryWrapper sourceWrapper = source.asGeometryWrapper();
		if (sourceWrapper.isEmpty()) {
			return ProjectedCandidateBounds.empty();
		}
		if (IndexGeometry.INDEX_CRS.equals(sourceWrapper.getSrsURI())) {
			return ProjectedCandidateBounds.nativeCrs84(
					new Envelope(sourceWrapper.getXYGeometry().getEnvelopeInternal()));
		}
		try {
			return transformSourceBounds(sourceWrapper);
		} catch (FactoryException | TransformException | RuntimeException ignored) {
			return ProjectedCandidateBounds.worldFallback(FALLBACK_TRANSFORM_FAILURE);
		}
	}

	private static ProjectedCandidateBounds transformSourceBounds(GeometryWrapper sourceWrapper)
			throws FactoryException, TransformException {
		CoordinateReferenceSystem sourceCrs = sourceWrapper.getCRS();
		CoordinateSystem sourceCoordinateSystem = sourceCrs.getCoordinateSystem();
		if (sourceCoordinateSystem != null && sourceCoordinateSystem.getDimension() == 3) {
			return transformThreeDimensionalSourceBounds(sourceWrapper, sourceCrs);
		}
		CoordinateReferenceSystem horizontal;
		try {
			horizontal = horizontalCrs(sourceCrs);
		} catch (TransformException e) {
			return ProjectedCandidateBounds.worldFallback(FALLBACK_MISSING_HORIZONTAL_CRS);
		}
		return transformHorizontalSourceBounds(sourceWrapper, horizontal);
	}

	private static ProjectedCandidateBounds transformThreeDimensionalSourceBounds(
			GeometryWrapper sourceWrapper, CoordinateReferenceSystem sourceCrs)
			throws FactoryException, TransformException {
		CoordinateReferenceSystem target3d = SRSRegistry.getCRS(WGS84_3D);
		GeneralEnvelope sourceEnvelope = createThreeDimensionalSourceEnvelope(sourceWrapper, sourceCrs);
		CoordinateOperation full3dOperation = CRS.findOperation(sourceCrs, target3d, null);
		org.opengis.geometry.Envelope target3dEnvelope = Envelopes.transform(full3dOperation, sourceEnvelope);
		CoordinateReferenceSystem targetCrs = SRSRegistry.getCRS(IndexGeometry.INDEX_CRS);
		CoordinateOperation reductionToCrs84 = CRS.findOperation(target3d, targetCrs, null);
		return toLuceneGeoBounds(Envelopes.transform(reductionToCrs84, target3dEnvelope));
	}

	private static ProjectedCandidateBounds transformHorizontalSourceBounds(
			GeometryWrapper sourceWrapper, CoordinateReferenceSystem sourceCrs)
			throws FactoryException, TransformException {
		CoordinateReferenceSystem targetCrs = SRSRegistry.getCRS(IndexGeometry.INDEX_CRS);
		GeneralEnvelope sourceEnvelope = createSourceEnvelope(sourceWrapper, sourceCrs);
		CoordinateOperation operation = CRS.findOperation(sourceCrs, targetCrs, null);
		org.opengis.geometry.Envelope transformed = Envelopes.transform(operation, sourceEnvelope);
		return toLuceneGeoBounds(transformed);
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

	private static GeneralEnvelope createThreeDimensionalSourceEnvelope(GeometryWrapper sourceWrapper,
			CoordinateReferenceSystem sourceCrs) throws TransformException {
		GeneralEnvelope sourceEnvelope = createSourceEnvelope(sourceWrapper, sourceCrs);
		double minZ = Double.POSITIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (Coordinate coordinate : sourceWrapper.getParsingGeometry().getCoordinates()) {
			double z = coordinate.getZ();
			if (!Double.isFinite(z)) {
				throw new TransformException("Three-dimensional source CRS requires finite vertical ordinates.");
			}
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}
		sourceEnvelope.setRange(2, minZ, maxZ);
		return sourceEnvelope;
	}

	/**
	 * Adapts a transformed envelope to one Lucene geographic rectangle, or returns the world CRS84 envelope.
	 *
	 * <p>SIS wraparound is read through {@code getMinimum}/{@code getMaximum}. A geographic envelope that already
	 * spans full longitude with a local latitude range is stored as that rectangle. The world CRS84 envelope is used
	 * only when the result is still unusable as one ordered Lucene geographic rectangle.
	 *
	 * <p>This path trusts a finite, ordered, in-range SIS rectangle after unit-in-the-last-place widening. It does
	 * not prove that rectangle covers the complete transformed geometry. A representable rectangle, including one that
	 * spans full longitude, remains {@link CandidateBoundsKind#TRANSFORMED}. World fallback is a representation path
	 * for an unusable rectangle, not a substitute for an unidentified unsafe {@code CoordinateOperation} class.
	 */
	static Envelope toLuceneGeoEnvelope(org.opengis.geometry.Envelope transformed) {
		return toLuceneGeoBounds(transformed).envelope();
	}

	static ProjectedCandidateBounds toLuceneGeoBounds(org.opengis.geometry.Envelope transformed) {
		if (transformed == null || transformed.getDimension() < 2) {
			return ProjectedCandidateBounds.worldFallback(FALLBACK_UNREPRESENTABLE_RECTANGLE);
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
			return ProjectedCandidateBounds.worldFallback(FALLBACK_UNREPRESENTABLE_RECTANGLE);
		}
		minX = Math.max(world.getMinX(), Math.nextDown(minX));
		maxX = Math.min(world.getMaxX(), Math.nextUp(maxX));
		minY = Math.max(world.getMinY(), Math.nextDown(minY));
		maxY = Math.min(world.getMaxY(), Math.nextUp(maxY));
		if (minX > maxX || minY > maxY) {
			return ProjectedCandidateBounds.worldFallback(FALLBACK_UNREPRESENTABLE_RECTANGLE);
		}
		return ProjectedCandidateBounds.transformed(new Envelope(minX, maxX, minY, maxY));
	}

	static Envelope worldCrs84Envelope() {
		Rectangle world = SpatialContext.GEO.getWorldBounds();
		return new Envelope(world.getMinX(), world.getMaxX(), world.getMinY(), world.getMaxY());
	}
}
