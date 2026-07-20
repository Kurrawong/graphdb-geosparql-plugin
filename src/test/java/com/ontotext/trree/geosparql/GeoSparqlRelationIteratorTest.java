package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import com.ontotext.trree.sdk.Entities;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.locationtech.jts.geom.Geometry;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
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
	public void candidateReturnedByIndexIsRejectedWhenExactRelationDoesNotHold() throws Exception {
		IndexGeometry polygon = indexGeometry("POLYGON((0 0,0 2,2 2,2 0,0 0))");
		IndexGeometry boundaryPoint = indexGeometry("POINT(2 1)");

		FakeEntities entities = new FakeEntities();
		entities.add(SUBJECT, SimpleValueFactory.getInstance().createIRI("http://example.com/boundary-point"));
		entities.add(OBJECT, SimpleValueFactory.getInstance().createIRI("http://example.com/container"));

		GeoSparqlPlugin plugin = new GeoSparqlPlugin();
		plugin.setLogger(LoggerFactory.getLogger(GeoSparqlRelationIteratorTest.class));
		plugin.indexer = new ScriptedGeoSparqlIndexer(singletonList(polygon), singletonList(polygon),
				singletonList(boundaryPoint), singletonList(boundaryPoint));

		GeoSparqlRelationIterator iterator = new GeoSparqlRelationIterator(plugin,
				GeoSparqlPropertyRelation.SF_WITHIN, 0, PREDICATE, OBJECT, entities);
		try {
			assertFalse(iterator.next());
		} finally {
			iterator.close();
		}
	}

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

	@Test
	public void fullScanReturnsEntityWithEmptyCollectionForDisjointRelation() throws Exception {
		IndexGeometry polygon = indexGeometry("POLYGON((0 0,0 2,2 2,2 0,0 0))");
		IndexGeometry emptyCollection = IndexGeometry.fromSourceGeometryLiteralComponents(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION EMPTY")).get(0);

		FakeEntities entities = new FakeEntities();
		entities.add(SUBJECT, SimpleValueFactory.getInstance().createIRI("http://example.com/empty"));
		entities.add(OBJECT, SimpleValueFactory.getInstance().createIRI("http://example.com/polygon"));

		GeoSparqlPlugin plugin = new GeoSparqlPlugin();
		plugin.setLogger(LoggerFactory.getLogger(GeoSparqlRelationIteratorTest.class));
		plugin.indexer = new ScriptedGeoSparqlIndexer(singletonList(polygon), singletonList(polygon),
				singletonList(emptyCollection), singletonList(emptyCollection),
				candidateIterator(SUBJECT, singletonList(emptyCollection)));

		GeoSparqlRelationIterator iterator = new GeoSparqlRelationIterator(plugin,
				GeoSparqlPropertyRelation.SF_DISJOINT, 0, PREDICATE, OBJECT, entities);
		try {
			assertTrue(iterator.next());
			assertEquals(SUBJECT, iterator.subject);
			assertEquals(OBJECT, iterator.object);
			assertFalse(iterator.next());
		} finally {
			iterator.close();
		}
	}

	@Test
	public void subjectBoundLookupChecksEveryDistinctCandidateSourceAndEmitsPairOnce() throws Exception {
		IndexGeometry boundPoint = indexGeometry("POINT(11 11)");
		IndexGeometry missPolygon = indexGeometry("POLYGON((0 0,0 2,2 2,2 0,0 0))");
		IndexGeometry hitPolygon = indexGeometry("POLYGON((10 10,10 12,12 12,12 10,10 10))");

		FakeEntities entities = new FakeEntities();
		entities.add(SUBJECT, SimpleValueFactory.getInstance().createLiteral(
				"POINT(11 11)", GeoConstants.GEO_WKT_LITERAL));
		entities.add(OBJECT, SimpleValueFactory.getInstance().createIRI("http://example.com/container"));

		GeoSparqlPlugin plugin = new GeoSparqlPlugin();
		plugin.setLogger(LoggerFactory.getLogger(GeoSparqlRelationIteratorTest.class));
		plugin.indexer = new ScriptedGeoSparqlIndexer(OBJECT, singletonList(boundPoint), singletonList(boundPoint),
				List.of(missPolygon, hitPolygon), List.of(missPolygon, hitPolygon));

		GeoSparqlRelationIterator iterator = new GeoSparqlRelationIterator(plugin,
				GeoSparqlPropertyRelation.SF_WITHIN, SUBJECT, PREDICATE, 0, entities);
		try {
			assertTrue(iterator.next());
			assertEquals(SUBJECT, iterator.subject);
			assertEquals(OBJECT, iterator.object);
			assertFalse(iterator.next());
		} finally {
			iterator.close();
		}
	}

	@Test
	public void boundCrossesPropertyRelationPermitsPointLineButRejectsLinePoint() {
		FakeEntities entities = new FakeEntities();
		entities.add(SUBJECT, SimpleValueFactory.getInstance().createLiteral(
				"GEOMETRYCOLLECTION(LINESTRING(0 0,2 0))", GeoConstants.GEO_WKT_LITERAL));
		entities.add(OBJECT, SimpleValueFactory.getInstance().createLiteral(
				"GEOMETRYCOLLECTION(MULTIPOINT((1 0),(1 1)))", GeoConstants.GEO_WKT_LITERAL));

		GeoSparqlPlugin plugin = new GeoSparqlPlugin();
		plugin.setLogger(LoggerFactory.getLogger(GeoSparqlRelationIteratorTest.class));

		GeoSparqlRelationIterator rejected = new GeoSparqlRelationIterator(plugin,
				GeoSparqlPropertyRelation.SF_CROSSES, SUBJECT, PREDICATE, OBJECT, entities);
		try {
			assertFalse(rejected.next());
		} finally {
			rejected.close();
		}

		GeoSparqlRelationIterator permitted = new GeoSparqlRelationIterator(plugin,
				GeoSparqlPropertyRelation.SF_CROSSES, OBJECT, PREDICATE, SUBJECT, entities);
		try {
			assertTrue(permitted.next());
			assertFalse(permitted.next());
		} finally {
			permitted.close();
		}
	}

	private static IndexGeometry indexGeometry(String wkt) {
		return TestIndexGeometries.exactlyOne(wkt);
	}

	private static final class ScriptedGeoSparqlIndexer implements GeoSparqlIndexer {
		private final List<IndexGeometry> objectMissGeometries;
		private final List<IndexGeometry> objectHitGeometries;
		private final List<IndexGeometry> firstCandidateGeometries;
		private final List<IndexGeometry> secondCandidateGeometries;
		private final CloseableIterator<CandidateEntity> allCandidates;
		private final long candidateEntityId;
		private int lookupCount;

		private ScriptedGeoSparqlIndexer(List<IndexGeometry> objectMissGeometries,
										 List<IndexGeometry> objectHitGeometries,
										 List<IndexGeometry> firstCandidateGeometries,
										 List<IndexGeometry> secondCandidateGeometries) {
			this(objectMissGeometries, objectHitGeometries, firstCandidateGeometries,
					secondCandidateGeometries, emptyIterator(), SUBJECT);
		}

		private ScriptedGeoSparqlIndexer(long candidateEntityId,
				List<IndexGeometry> objectMissGeometries,
				List<IndexGeometry> objectHitGeometries,
				List<IndexGeometry> firstCandidateGeometries,
				List<IndexGeometry> secondCandidateGeometries) {
			this(objectMissGeometries, objectHitGeometries, firstCandidateGeometries,
					secondCandidateGeometries, emptyIterator(), candidateEntityId);
		}

		private ScriptedGeoSparqlIndexer(List<IndexGeometry> objectMissGeometries,
										 List<IndexGeometry> objectHitGeometries,
										 List<IndexGeometry> firstCandidateGeometries,
										 List<IndexGeometry> secondCandidateGeometries,
										 CloseableIterator<CandidateEntity> allCandidates) {
			this(objectMissGeometries, objectHitGeometries, firstCandidateGeometries,
					secondCandidateGeometries, allCandidates, SUBJECT);
		}

		private ScriptedGeoSparqlIndexer(List<IndexGeometry> objectMissGeometries,
										 List<IndexGeometry> objectHitGeometries,
										 List<IndexGeometry> firstCandidateGeometries,
										 List<IndexGeometry> secondCandidateGeometries,
										 CloseableIterator<CandidateEntity> allCandidates,
										 long candidateEntityId) {
			this.objectMissGeometries = objectMissGeometries;
			this.objectHitGeometries = objectHitGeometries;
			this.firstCandidateGeometries = firstCandidateGeometries;
			this.secondCandidateGeometries = secondCandidateGeometries;
			this.allCandidates = allCandidates;
			this.candidateEntityId = candidateEntityId;
		}

		@Override
		public CloseableIterator<CandidateEntity> getMatchingEntities(Geometry geometry,
				CandidateLookupPolicy candidateLookupPolicy) {
			lookupCount++;
			if (lookupCount == 1) {
				return candidateIterator(candidateEntityId, firstCandidateGeometries);
			}
			if (lookupCount == 2) {
				return candidateIterator(candidateEntityId, secondCandidateGeometries);
			}
			return emptyIterator();
		}

		@Override
		public CloseableIterator<CandidateEntity> getAllEntities() {
			return allCandidates;
		}

		@Override
		public CloseableIterator<IndexGeometry> getGeometriesFor(long subject) {
			if (subject == OBJECT) {
				return iterator(List.of(
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

	private static CloseableIterator<CandidateEntity> candidateIterator(long entityId,
			List<IndexGeometry> geometries) {
		return iterator(List.of(new CandidateEntity(entityId, geometries)));
	}

	private static <T> CloseableIterator<T> emptyIterator() {
		return iterator(List.of());
	}

	private static <T> CloseableIterator<T> iterator(List<T> values) {
		Iterator<T> iterator = values.iterator();
		return new CloseableIterator<T>() {
			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public T next() {
				return iterator.next();
			}

			@Override
			public void close() throws IOException {
			}
		};
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
