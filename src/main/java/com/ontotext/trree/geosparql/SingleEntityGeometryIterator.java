package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import org.locationtech.jts.geom.Geometry;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Iterator implementation for a single entity and a static list of geometries.
 */
public class SingleEntityGeometryIterator implements EntityGeometryIterator {
	private final long entityId;
	private final Iterator<IndexGeometry> iGeometries;
	private Geometry geometry;
	private SourceGeometryLiteral sourceGeometryLiteral;
	private IndexGeometry indexGeometry;

	public SingleEntityGeometryIterator(long entityId, List<IndexGeometry> geometries) {
		this.entityId = entityId;
		this.iGeometries = geometries != null ? geometries.iterator() : Collections.emptyIterator();
	}

	public SingleEntityGeometryIterator(long entityId, IndexGeometry geometry) {
		this.entityId = entityId;
		this.iGeometries = geometry != null ? Collections.singletonList(geometry).iterator()
				: Collections.emptyIterator();
	}

	@Override
	public long getEntityForLastGeometry() {
		return entityId;
	}

	@Override
	public Geometry nextGeometry() {
		indexGeometry = iGeometries.next();
		sourceGeometryLiteral = indexGeometry.sourceGeometryLiteral();
		return geometry = indexGeometry.indexGeometry();
	}

	@Override
	public Geometry lastGeometry() {
		return geometry;
	}

	@Override
	public SourceGeometryLiteral lastSourceGeometryLiteral() {
		return sourceGeometryLiteral;
	}

	@Override
	public IndexGeometry lastIndexGeometry() {
		return indexGeometry;
	}

	@Override
	public boolean hasNextGeometry() {
		return iGeometries.hasNext();
	}

	@Override
	public void advanceToNextEntity() {

	}

	@Override
	public void close() throws IOException {
		// does nothing
	}
}
