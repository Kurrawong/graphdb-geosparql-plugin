package com.ontotext.trree.geosparql;

import java.io.Closeable;

/**
 * Iterator over unique entity ids returned by candidate lookup.
 */
public interface EntityIdIterator extends Closeable {
	/**
	 * Checks if there is another entity id.
	 *
	 * @return true if there is another entity id, false otherwise
	 */
	boolean hasNextEntityId();

	/**
	 * Returns the next entity id.
	 *
	 * @return a GraphDB entity id, or 0 if there are no more entity ids
	 */
	long nextEntityId();

	/**
	 * Returns source geometry literal snapshots and index geometries for the entity returned by the last
	 * call to nextEntityId(). Callers should use this before advancing the iterator again.
	 *
	 * @return an iterator over the last entity's geometries
	 */
	EntityGeometryIterator getGeometriesForLastEntity();
}
