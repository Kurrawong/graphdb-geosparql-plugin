package com.ontotext.trree.geosparql.jena;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;

/**
 * Associates a source geometry literal representation with one derived Lucene index geometry.
 *
 * <p>The index geometry is transformed to CRS84 when necessary so it can be
 * indexed by Lucene's spatial layer for coarse candidate lookup. It is not the
 * geometry used for exact GeoSPARQL relation evaluation; exact evaluation uses
 * the source geometry literal, including its datatype and effective source CRS.
 *
 * <p>A non-empty generic geometry collection uses the envelope of the complete CRS84-transformed collection. An
 * empty collection produces a non-spatial sentinel that retains the entity and source metadata for full scans.
 */
public final class IndexGeometry {
	public static final String INDEX_CRS = SRS_URI.DEFAULT_WKT_CRS84;
	public static final String BUILD_MODE_TRANSFORMED_GEOMETRY = "transformed-geometry";
	public static final String BUILD_MODE_TRANSFORMED_ENVELOPE = "transformed-envelope";
	public static final String BUILD_MODE_EMPTY_SENTINEL = "empty-sentinel";

	private final SourceGeometryLiteral sourceGeometryLiteral;
	private final Geometry indexGeometry;
	private final String indexCrs;
	private final String indexBuildMode;

	private IndexGeometry(SourceGeometryLiteral sourceGeometryLiteral, Geometry indexGeometry, String indexCrs,
						  String indexBuildMode) {
		this.sourceGeometryLiteral = sourceGeometryLiteral;
		this.indexGeometry = indexGeometry;
		this.indexCrs = indexCrs;
		this.indexBuildMode = indexBuildMode;
	}

	public static IndexGeometry fromSourceGeometryLiteral(SourceGeometryLiteral sourceGeometryLiteral) {
		try {
			GeometryWrapper sourceWrapper = sourceGeometryLiteral.asGeometryWrapper();
			GeometryWrapper indexWrapper = INDEX_CRS.equals(sourceWrapper.getSrsURI())
					? sourceWrapper
					: sourceWrapper.transform(INDEX_CRS);
			Geometry transformed = indexWrapper.getXYGeometry().copy();
			if (transformed.getClass().equals(GeometryCollection.class)) {
				if (transformed.isEmpty()) {
					return new IndexGeometry(sourceGeometryLiteral, transformed, INDEX_CRS,
							BUILD_MODE_EMPTY_SENTINEL);
				}
				Geometry envelope = transformed.getFactory().toGeometry(transformed.getEnvelopeInternal());
				return new IndexGeometry(sourceGeometryLiteral, envelope, INDEX_CRS,
						BUILD_MODE_TRANSFORMED_ENVELOPE);
			}
			return new IndexGeometry(sourceGeometryLiteral, transformed, INDEX_CRS,
					BUILD_MODE_TRANSFORMED_GEOMETRY);
		} catch (JenaGeoSparqlException e) {
			throw e;
		} catch (Exception e) {
			throw new JenaGeoSparqlException("Unable to derive CRS84 index geometry from source geometry literal. "
					+ "Configure Apache SIS CRS data, for example SIS_DATA, if the CRS is supported.", e);
		}
	}

	public static IndexGeometry fromStoredMetadata(Geometry indexGeometry,
			SourceGeometryLiteral sourceGeometryLiteral, String effectiveSourceCrsUri, String indexCrs,
			String indexBuildMode) {
		if (effectiveSourceCrsUri == null) {
			throw new JenaGeoSparqlException("Lucene source geometry literal CRS metadata is missing.");
		}
		if (!sourceGeometryLiteral.effectiveCrsUri().equals(effectiveSourceCrsUri)) {
			throw new JenaGeoSparqlException("Lucene source geometry literal CRS metadata does not match "
					+ "effective source CRS.");
		}
		if (!INDEX_CRS.equals(indexCrs)) {
			throw new JenaGeoSparqlException("Unsupported GeoSPARQL Lucene index CRS: " + indexCrs);
		}
		if (!BUILD_MODE_TRANSFORMED_GEOMETRY.equals(indexBuildMode)
				&& !BUILD_MODE_TRANSFORMED_ENVELOPE.equals(indexBuildMode)
				&& !BUILD_MODE_EMPTY_SENTINEL.equals(indexBuildMode)) {
			throw new JenaGeoSparqlException("Unsupported GeoSPARQL Lucene index build mode: " + indexBuildMode);
		}
		return new IndexGeometry(sourceGeometryLiteral, indexGeometry, indexCrs, indexBuildMode);
	}

	public SourceGeometryLiteral sourceGeometryLiteral() {
		return sourceGeometryLiteral;
	}

	public Geometry indexGeometry() {
		return indexGeometry;
	}

	public String indexCrs() {
		return indexCrs;
	}

	public String indexBuildMode() {
		return indexBuildMode;
	}

	public boolean isSpatialCandidate() {
		return !BUILD_MODE_EMPTY_SENTINEL.equals(indexBuildMode);
	}

	public boolean isGenericCollectionSource() {
		return BUILD_MODE_TRANSFORMED_ENVELOPE.equals(indexBuildMode)
				|| BUILD_MODE_EMPTY_SENTINEL.equals(indexBuildMode);
	}
}
