package com.ontotext.trree.geosparql.jena;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Envelope;

/**
 * Associates a source geometry literal with its derived Lucene index envelope.
 *
 * <p>The complete source geometry is transformed to CRS84 before its envelope is derived. The envelope is used only
 * for coarse Lucene candidate lookup. Exact GeoSPARQL evaluation uses the source geometry literal, including its
 * datatype and effective source CRS. An empty source has a null envelope and is represented by a non-spatial Lucene
 * document so full scans can still reconstruct it.
 */
public final class IndexGeometry {
	public static final String INDEX_CRS = SRS_URI.DEFAULT_WKT_CRS84;

	private final SourceGeometryLiteral sourceGeometryLiteral;
	private final Envelope indexEnvelope;

	private IndexGeometry(SourceGeometryLiteral sourceGeometryLiteral, Envelope indexEnvelope) {
		this.sourceGeometryLiteral = sourceGeometryLiteral;
		this.indexEnvelope = new Envelope(indexEnvelope);
	}

	public static IndexGeometry fromSourceGeometryLiteral(SourceGeometryLiteral sourceGeometryLiteral) {
		try {
			GeometryWrapper sourceWrapper = sourceGeometryLiteral.asGeometryWrapper();
			GeometryWrapper indexWrapper = INDEX_CRS.equals(sourceWrapper.getSrsURI())
					? sourceWrapper
					: sourceWrapper.transform(INDEX_CRS);
			Geometry transformed = indexWrapper.getXYGeometry();
			return new IndexGeometry(sourceGeometryLiteral, transformed.getEnvelopeInternal());
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

	public boolean isSpatialCandidate() {
		return !indexEnvelope.isNull();
	}
}
