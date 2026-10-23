package com.ontotext.trree.geosparql.jena;

/**
 * Declares whether a geometry-returning query operation has defined altitude provenance in GeoJSON and KML results.
 */
public enum Crs84ResultDimensionPolicy {
	XY_ONLY,
	PRESERVE_DEFINED_Z
}
