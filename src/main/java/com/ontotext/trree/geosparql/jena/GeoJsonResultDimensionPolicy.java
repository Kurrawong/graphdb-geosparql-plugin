package com.ontotext.trree.geosparql.jena;

/**
 * Declares whether a geometry-returning query operation has defined altitude provenance in GeoJSON results.
 */
public enum GeoJsonResultDimensionPolicy {
	XY_ONLY,
	PRESERVE_DEFINED_Z
}
