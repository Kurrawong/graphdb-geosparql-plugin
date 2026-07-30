package com.ontotext.trree.geosparql.jena;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.relateng.RelateNG;
import org.locationtech.jts.operation.relateng.RelatePredicate;

/**
 * Associates a source geometry literal with its derived Lucene index envelope.
 *
 * <p>The complete source geometry is transformed to CRS84 before its envelope is derived. The envelope is used only
 * for coarse Lucene lookup, conservative envelope-separation proofs, and eligible rectangular-envelope containment
 * proofs. Exact GeoSPARQL evaluation uses the source geometry literal, including its datatype and effective source
 * CRS. An empty source has a null envelope and is represented by a non-spatial Lucene document so exact traversal can
 * still reconstruct it.
 */
public final class IndexGeometry {
	public static final String INDEX_CRS = SRS_URI.DEFAULT_WKT_CRS84;

	private final SourceGeometryLiteral sourceGeometryLiteral;
	private final Envelope indexEnvelope;
	// Cache the bound source dimension derived during index-geometry construction,
	// so relation traversal does not need to navigate Jena geometry metadata.
	private final int sourceTopologicalDimension;

	private IndexGeometry(SourceGeometryLiteral sourceGeometryLiteral, Envelope indexEnvelope,
			int sourceTopologicalDimension) {
		this.sourceGeometryLiteral = sourceGeometryLiteral;
		this.indexEnvelope = new Envelope(indexEnvelope);
		this.sourceTopologicalDimension = sourceTopologicalDimension;
	}

	public static IndexGeometry fromSourceGeometryLiteral(SourceGeometryLiteral sourceGeometryLiteral) {
		try {
			GeometryWrapper sourceWrapper = sourceGeometryLiteral.asGeometryWrapper();
			GeometryWrapper indexWrapper = INDEX_CRS.equals(sourceWrapper.getSrsURI())
					? sourceWrapper
					: sourceWrapper.transform(INDEX_CRS);
			Geometry transformed = indexWrapper.getXYGeometry();
			return new IndexGeometry(sourceGeometryLiteral, transformed.getEnvelopeInternal(),
					sourceWrapper.getDimensionInfo().getTopological());
		} catch (JenaGeoSparqlException e) {
			throw e;
		} catch (Exception e) {
			throw new JenaGeoSparqlException("Unable to derive CRS84 index geometry from source geometry literal. "
					+ "Configure Apache SIS CRS data, for example SIS_DATA, if the CRS is supported.", e);
		}
	}

	public SourceGeometryLiteral sourceGeometryLiteral() {
		return sourceGeometryLiteral;
	}

	public Envelope indexEnvelope() {
		return new Envelope(indexEnvelope);
	}

	public String indexCrs() {
		return INDEX_CRS;
	}

	/** Returns the cached source topological dimension used for candidate classification. */
	public int sourceTopologicalDimension() {
		return sourceTopologicalDimension;
	}

	/**
	 * Returns whether the source is a non-empty CRS84 polygon that covers every point in its index envelope.
	 *
	 * <p>This deliberately excludes transformed sources, holes, rotated polygons, and collections. For an eligible
	 * bound, containment of another source's exact index envelope proves that the two sources cannot be disjoint.
	 */
	public boolean isEnvelopeCoveringRectangle() {
		GeometryWrapper sourceWrapper = sourceGeometryLiteral.asGeometryWrapper();
		Geometry sourceGeometry = sourceWrapper.getXYGeometry();
		if (!INDEX_CRS.equals(sourceWrapper.getSrsURI())
				|| sourceGeometry.isEmpty()
				|| !(sourceGeometry instanceof Polygon)) {
			return false;
		}
		try {
			return RelateNG.relate(
					sourceGeometry, sourceGeometry.getEnvelope(), RelatePredicate.equalsTopo());
		} catch (RuntimeException e) {
			// Rectangle containment is an optional proof. Invalid topology must disable pruning,
			// not prevent the query from falling back to exact evaluation.
			return false;
		}
	}

	public boolean isSpatialCandidate() {
		return !indexEnvelope.isNull();
	}
}
