package com.ontotext.trree.geosparql.jena;

/**
 * How an {@link IndexGeometry} CRS84 candidate envelope was produced.
 *
 * <p>{@link #EMPTY} is the non-spatial sentinel for an empty source. {@link #NATIVE_CRS84} is the source envelope of
 * a CRS84 geometry. {@link #TRANSFORMED} is a selective SIS {@code CoordinateOperation} envelope, including full 3D
 * operations for sources with a three-dimensional CRS and horizontal operations for ordinary GDA2020 and projected
 * MGA2020 sources. {@link #WORLD_FALLBACK} is the geographic world rectangle used only when that envelope cannot be
 * stored as one Lucene rectangle, the source has no usable horizontal CRS, or transform construction fails.
 *
 * <p>This is indexing diagnostics only. It is not persisted in Lucene.
 */
public enum CandidateBoundsKind {
	EMPTY,
	NATIVE_CRS84,
	TRANSFORMED,
	WORLD_FALLBACK
}
