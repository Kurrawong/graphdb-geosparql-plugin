package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.sdk.Entities;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.locationtech.jts.geom.Geometry;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeoSparqlRelationIteratorTest {
	private static final long SUBJECT = 1L;
	private static final long OBJECT = 2L;
	private static final long PREDICATE = 3L;

	@Test
	public void objectBoundLookupReconsidersCandidateAfterEarlierBoundGeometryExactMiss() throws Exception {
		IndexGeometry missPolygon = indexGeometry("POLYGON((0 0,0 2,2 2,2 0,0 0))");
		IndexGeometry hitPolygon = indexGeometry("POLYGON((10 10,10 12,12 12,12 10,10 10))");
		IndexGeometry boundaryPoint = indexGeometry("POINT(2 1)");
		IndexGeometry insidePoint = indexGeometry("POINT(11 11)");

		FakeEntities entities = new FakeEntities();
		entities.add(SUBJECT, SimpleValueFactory.getInstance().createIRI("http://example.com/thing"));
		entities.add(OBJECT, SimpleValueFactory.getInstance().createIRI("http://example.com/container"));

		GeoSparqlPlugin plugin = new GeoSparqlPlugin();
		plugin.setLogger(LoggerFactory.getLogger(GeoSparqlRelationIteratorTest.class));
		plugin.indexer = new ScriptedGeoSparqlIndexer(singletonList(missPolygon), singletonList(hitPolygon),
				singletonList(boundaryPoint), singletonList(insidePoint));

		GeoSparqlRelationIterator iterator = new GeoSparqlRelationIterator(plugin,
				GeoSparqlPropertyRelation.SF_WITHIN, 0, PREDICATE, OBJECT, entities);
		try {
			assertTrue(iterator.next());
			assertEquals(SUBJECT, iterator.subject);
			assertEquals(OBJECT, iterator.object);
			assertFalse(iterator.next());
		} finally {
			iterator.close();
		}
	}

	private static IndexGeometry indexGeometry(String wkt) {
		return IndexGeometry.fromSourceGeometryLiteral(SourceGeometryLiteral.fromWkt(wkt));
	}

	private static final class ScriptedGeoSparqlIndexer implements GeoSparqlIndexer {
		private final List<IndexGeometry> objectMissGeometries;
		private final List<IndexGeometry> objectHitGeometries;
		private final List<IndexGeometry> firstCandidateGeometries;
		private final List<IndexGeometry> secondCandidateGeometries;
		private int lookupCount;

		private ScriptedGeoSparqlIndexer(List<IndexGeometry> objectMissGeometries,
										 List<IndexGeometry> objectHitGeometries,
										 List<IndexGeometry> firstCandidateGeometries,
										 List<IndexGeometry> secondCandidateGeometries) {
			this.objectMissGeometries = objectMissGeometries;
			this.objectHitGeometries = objectHitGeometries;
			this.firstCandidateGeometries = firstCandidateGeometries;
			this.secondCandidateGeometries = secondCandidateGeometries;
		}

		@Override
		public EntityIdIterator getMatchingEntityIds(Geometry geometry, CandidateLookupPolicy candidateLookupPolicy) {
			lookupCount++;
			if (lookupCount == 1) {
				return new SingleCandidateEntityIdIterator(SUBJECT, firstCandidateGeometries);
			}
			if (lookupCount == 2) {
				return new SingleCandidateEntityIdIterator(SUBJECT, secondCandidateGeometries);
			}
			return new EmptyEntityIdIterator();
		}

		@Override
		public EntityGeometryIterator getGeometriesFor(long subject) {
			if (subject == OBJECT) {
				return new SingleEntityGeometryIterator(subject, List.of(
						objectMissGeometries.get(0),
						objectHitGeometries.get(0)));
			}
			throw new UnsupportedOperationException();
		}

		@Override
		public void initialize() throws Exception {
		}

		@Override
		public void indexGeometryList(long subject, Function<Long, String> subjectMapper,
									  List<IndexGeometry> geometries) {
		}

		@Override
		public EntityGeometryIterator getMatchingObjects(Geometry geometry,
														 CandidateLookupPolicy candidateLookupPolicy) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void initSettings() {
		}

		@Override
		public void begin() throws Exception {
		}

		@Override
		public void commit() throws Exception {
		}

		@Override
		public void rollback() throws Exception {
		}

		@Override
		public void indexGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry) {
		}

		@Override
		public void freshIndex() throws Exception {
		}
	}

	private static final class SingleCandidateEntityIdIterator implements EntityIdIterator {
		private final long entityId;
		private final List<IndexGeometry> geometries;
		private boolean consumed;
		private boolean lastReady;

		private SingleCandidateEntityIdIterator(long entityId, List<IndexGeometry> geometries) {
			this.entityId = entityId;
			this.geometries = geometries;
		}

		@Override
		public boolean hasNextEntityId() {
			return !consumed;
		}

		@Override
		public long nextEntityId() {
			if (consumed) {
				return 0;
			}
			consumed = true;
			lastReady = true;
			return entityId;
		}

		@Override
		public EntityGeometryIterator getGeometriesForLastEntity() {
			if (!lastReady) {
				throw new IllegalStateException();
			}
			return new SingleEntityGeometryIterator(entityId, geometries);
		}

		@Override
		public void close() throws IOException {
		}
	}

	private static final class EmptyEntityIdIterator implements EntityIdIterator {
		@Override
		public boolean hasNextEntityId() {
			return false;
		}

		@Override
		public long nextEntityId() {
			return 0;
		}

		@Override
		public EntityGeometryIterator getGeometriesForLastEntity() {
			throw new IllegalStateException();
		}

		@Override
		public void close() throws IOException {
		}
	}

	private static final class FakeEntities implements Entities {
		private final Map<Long, Value> values = new HashMap<>();

		private void add(long id, Value value) {
			values.put(id, value);
		}

		@Override
		public Value get(long id) {
			return values.get(id);
		}

		@Override
		public Type getType(long id) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getLanguage(long id) {
			throw new UnsupportedOperationException();
		}

		@Override
		public IRI getDatatype(long id) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long getClass(long id) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long put(Value value, Scope scope) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long replace(long id, Value value, Scope scope) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long resolve(Value value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long size() {
			return values.size();
		}

		@Override
		public boolean isTransactional() {
			return false;
		}

		@Override
		public int getEntityIdSize() {
			return Long.BYTES;
		}
	}
}
