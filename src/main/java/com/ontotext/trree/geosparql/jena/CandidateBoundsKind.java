package com.ontotext.trree.geosparql.jena;

/**
 * How an {@link IndexGeometry} CRS84 candidate envelope was produced.
 *
 * <p>This is indexing diagnostics only. It is not persisted in Lucene.
 */
public enum CandidateBoundsKind {
	EMPTY,
	NATIVE_CRS84,
	TRANSFORMED,
	WORLD_FALLBACK
}
