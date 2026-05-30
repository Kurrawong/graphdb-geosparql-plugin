package com.ontotext.trree.geosparql.jena;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.locationtech.jts.geom.Geometry;

/**
 * Derived geometry used only for Lucene candidate lookup.
 */
public final class IndexGeometry {
	public static final int SCHEMA_VERSION = 2;
	public static final String FIELD_SCHEMA_VERSION = "geoSchemaVersion";
	public static final String FIELD_EXACT_LEXICAL_FORM = "geoExactLexicalForm";
	public static final String FIELD_EXACT_DATATYPE = "geoExactDatatype";
	public static final String FIELD_EXACT_CRS = "geoExactCrs";
	public static final String FIELD_INDEX_CRS = "geoIndexCrs";
	public static final String FIELD_INDEX_BUILD_MODE = "geoIndexBuildMode";

	public static final String INDEX_CRS = SRS_URI.DEFAULT_WKT_CRS84;
	public static final String BUILD_MODE_TRANSFORMED_GEOMETRY = "transformed-geometry";

	private final ExactGeometry exactGeometry;
	private final Geometry indexGeometry;
	private final String indexCrs;
	private final String indexBuildMode;

	private IndexGeometry(ExactGeometry exactGeometry, Geometry indexGeometry, String indexCrs, String indexBuildMode) {
		this.exactGeometry = exactGeometry;
		this.indexGeometry = indexGeometry;
		this.indexCrs = indexCrs;
		this.indexBuildMode = indexBuildMode;
	}

	public static IndexGeometry fromExactGeometry(ExactGeometry exactGeometry) {
		try {
			GeometryWrapper exactWrapper = exactGeometry.asGeometryWrapper();
			GeometryWrapper indexWrapper = INDEX_CRS.equals(exactWrapper.getSrsURI())
					? exactWrapper
					: exactWrapper.transform(INDEX_CRS);
			return new IndexGeometry(exactGeometry, indexWrapper.getXYGeometry().copy(), INDEX_CRS,
					BUILD_MODE_TRANSFORMED_GEOMETRY);
		} catch (JenaGeoSparqlException e) {
			throw e;
		} catch (Exception e) {
			throw new JenaGeoSparqlException("Unable to derive CRS84 index geometry from exact geometry. "
					+ "Configure Apache SIS CRS data, for example SIS_DATA, if the CRS is supported.", e);
		}
	}

	public static IndexGeometry fromStoredMetadata(Geometry indexGeometry, String lexicalForm, String datatype,
												  String explicitCrsUri, String indexCrs, String indexBuildMode) {
		ExactGeometry exactGeometry = ExactGeometry.fromLiteral(
				org.eclipse.rdf4j.model.impl.SimpleValueFactory.getInstance()
						.createLiteral(lexicalForm,
								org.eclipse.rdf4j.model.impl.SimpleValueFactory.getInstance().createIRI(datatype)));
		if (explicitCrsUri != null && !exactGeometry.explicitCrsUri().orElse("").equals(explicitCrsUri)) {
			throw new JenaGeoSparqlException("Lucene exact geometry CRS metadata does not match lexical form.");
		}
		if (!INDEX_CRS.equals(indexCrs)) {
			throw new JenaGeoSparqlException("Unsupported GeoSPARQL Lucene index CRS: " + indexCrs);
		}
		if (!BUILD_MODE_TRANSFORMED_GEOMETRY.equals(indexBuildMode)) {
			throw new JenaGeoSparqlException("Unsupported GeoSPARQL Lucene index build mode: " + indexBuildMode);
		}
		return new IndexGeometry(exactGeometry, indexGeometry, indexCrs, indexBuildMode);
	}

	public ExactGeometry exactGeometry() {
		return exactGeometry;
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
