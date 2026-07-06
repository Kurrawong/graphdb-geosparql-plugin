package com.ontotext.trree.geosparql.jena;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.locationtech.jts.geom.Geometry;

/**
 * Derived geometry used only for Lucene candidate lookup.
 */
public final class IndexGeometry {
	public static final String INDEX_CRS = SRS_URI.DEFAULT_WKT_CRS84;
	public static final String BUILD_MODE_TRANSFORMED_GEOMETRY = "transformed-geometry";

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
			return new IndexGeometry(sourceGeometryLiteral, indexWrapper.getXYGeometry().copy(), INDEX_CRS,
					BUILD_MODE_TRANSFORMED_GEOMETRY);
		} catch (JenaGeoSparqlException e) {
			throw e;
		} catch (Exception e) {
			throw new JenaGeoSparqlException("Unable to derive CRS84 index geometry from source geometry literal. "
					+ "Configure Apache SIS CRS data, for example SIS_DATA, if the CRS is supported.", e);
		}
	}

	public static IndexGeometry fromStoredMetadata(Geometry indexGeometry, String lexicalForm, String datatype,
												  String effectiveSourceCrsUri, String indexCrs,
												  String indexBuildMode) {
		SourceGeometryLiteral sourceGeometryLiteral = SourceGeometryLiteral.fromLiteral(
				org.eclipse.rdf4j.model.impl.SimpleValueFactory.getInstance()
						.createLiteral(lexicalForm,
								org.eclipse.rdf4j.model.impl.SimpleValueFactory.getInstance().createIRI(datatype)));
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
		if (!BUILD_MODE_TRANSFORMED_GEOMETRY.equals(indexBuildMode)) {
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
}
