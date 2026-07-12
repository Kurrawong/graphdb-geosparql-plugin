package com.ontotext.trree.geosparql.jena;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pair of the authoritative source geometry literal and the derived geometry
 * used for Lucene candidate lookup.
 *
 * <p>The index geometry is transformed to CRS84 when necessary so it can be
 * indexed by Lucene's spatial layer for coarse candidate lookup. It is not the
 * geometry used for exact GeoSPARQL relation evaluation; exact evaluation uses
 * the source geometry literal, including its datatype and effective source CRS.
 */
public final class IndexGeometry {
	public static final String INDEX_CRS = SRS_URI.DEFAULT_WKT_CRS84;
	public static final String BUILD_MODE_TRANSFORMED_GEOMETRY = "transformed-geometry";
	public static final String BUILD_MODE_TRANSFORMED_COMPONENT = "transformed-component";
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
		List<IndexGeometry> geometries = fromSourceGeometryLiteralComponents(sourceGeometryLiteral);
		if (geometries.size() != 1) {
			throw new JenaGeoSparqlException("Source geometry literal produces " + geometries.size()
					+ " component index geometries; use fromSourceGeometryLiteralComponents().");
		}
		return geometries.get(0);
	}

	public static List<IndexGeometry> fromSourceGeometryLiteralComponents(
			SourceGeometryLiteral sourceGeometryLiteral) {
		try {
			GeometryWrapper sourceWrapper = sourceGeometryLiteral.asGeometryWrapper();
			GeometryWrapper indexWrapper = INDEX_CRS.equals(sourceWrapper.getSrsURI())
					? sourceWrapper
					: sourceWrapper.transform(INDEX_CRS);
			Geometry transformed = indexWrapper.getXYGeometry().copy();
			if (transformed.getClass().equals(GeometryCollection.class)) {
				List<IndexGeometry> components = new ArrayList<>();
				addComponents(sourceGeometryLiteral, (GeometryCollection) transformed, components);
				if (components.isEmpty()) {
					components.add(new IndexGeometry(sourceGeometryLiteral, transformed, INDEX_CRS,
							BUILD_MODE_EMPTY_SENTINEL));
				}
				return Collections.unmodifiableList(components);
			}
			return Collections.singletonList(new IndexGeometry(sourceGeometryLiteral, transformed, INDEX_CRS,
					BUILD_MODE_TRANSFORMED_GEOMETRY));
		} catch (JenaGeoSparqlException e) {
			throw e;
		} catch (Exception e) {
			throw new JenaGeoSparqlException("Unable to derive CRS84 index geometry from source geometry literal. "
					+ "Configure Apache SIS CRS data, for example SIS_DATA, if the CRS is supported.", e);
		}
	}

	private static void addComponents(SourceGeometryLiteral sourceGeometryLiteral, GeometryCollection collection,
			List<IndexGeometry> components) {
		for (int i = 0; i < collection.getNumGeometries(); i++) {
			Geometry component = collection.getGeometryN(i);
			if (component.isEmpty()) {
				continue;
			}
			if (component.getClass().equals(GeometryCollection.class)) {
				addComponents(sourceGeometryLiteral, (GeometryCollection) component, components);
			} else {
				components.add(new IndexGeometry(sourceGeometryLiteral, component.copy(), INDEX_CRS,
						BUILD_MODE_TRANSFORMED_COMPONENT));
			}
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
		if (!BUILD_MODE_TRANSFORMED_GEOMETRY.equals(indexBuildMode)
				&& !BUILD_MODE_TRANSFORMED_COMPONENT.equals(indexBuildMode)
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
}
