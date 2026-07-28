package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RelationCandidateTraversalTest {
	private static final Logger LOG = LoggerFactory.getLogger(RelationCandidateTraversalTest.class);

	@Test
	public void fullScanOpensOnceAndClosesItsReader() {
		IndexGeometry bound = geometry("POINT(0 0)");
		TrackingGeoSparqlIndexer indexer = new TrackingGeoSparqlIndexer();
		indexer.fullScanCandidates = List.of(candidate(1L, geometry("POINT(1 1)")));

		RelationCandidateTraversal traversal = new RelationCandidateTraversal(indexer,
				CandidateLookupPolicy.FULL_SCAN, List.of(bound), LOG);

		assertTrue(traversal.hasNext());
		assertTrue(traversal.hasNext());
		RelationCandidateTraversal.Candidate result = traversal.next();
		assertEquals(1L, result.candidateEntity().entityId());
		assertTrue(result.boundSourceGeometryLiteral().isEmpty());
		assertFalse(traversal.hasNext());
		assertFalse(traversal.hasNext());
		assertEquals(1, indexer.fullScanLookupCount);
		assertFalse(indexer.fullScanIterator.closed);

		traversal.close();
		assertTrue(indexer.fullScanIterator.closed);
	}

	@Test
	public void envelopeTraversalSkipsEmptyAndRetainsBoundSource() {
		IndexGeometry first = geometry("POINT(0 0)");
		IndexGeometry empty = geometry("GEOMETRYCOLLECTION EMPTY");
		IndexGeometry second = geometry("POINT(10 10)");
		TrackingGeoSparqlIndexer indexer = new TrackingGeoSparqlIndexer();
		indexer.addEnvelopeCandidates(first, candidate(1L, geometry("POINT(0 0)")));
		indexer.addEnvelopeCandidates(second, candidate(2L, geometry("POINT(10 10)")));

		RelationCandidateTraversal traversal = new RelationCandidateTraversal(indexer,
				CandidateLookupPolicy.ENVELOPE_INTERSECTS,
				List.of(first, empty, second), LOG);

		RelationCandidateTraversal.Candidate firstResult = traversal.next();
		assertEquals(1L, firstResult.candidateEntity().entityId());
		assertSame(first.sourceGeometryLiteral(), firstResult.boundSourceGeometryLiteral().orElseThrow());

		RelationCandidateTraversal.Candidate secondResult = traversal.next();
		assertEquals(2L, secondResult.candidateEntity().entityId());
		assertSame(second.sourceGeometryLiteral(), secondResult.boundSourceGeometryLiteral().orElseThrow());

		assertFalse(traversal.hasNext());
		assertEquals(List.of(first.sourceGeometryLiteral(), second.sourceGeometryLiteral()),
				indexer.envelopeLookupSources);
		assertEquals(List.of(
				"open:POINT(0 0)",
				"close:POINT(0 0)",
				"open:POINT(10 10)",
				"close:POINT(10 10)"), indexer.events);
	}

	@Test
	public void earlyCloseClosesActiveEnvelopeReaderWithoutOpeningAnother() {
		IndexGeometry first = geometry("POINT(0 0)");
		IndexGeometry second = geometry("POINT(10 10)");
		TrackingGeoSparqlIndexer indexer = new TrackingGeoSparqlIndexer();
		indexer.addEnvelopeCandidates(first, candidate(1L, geometry("POINT(0 0)")));
		indexer.addEnvelopeCandidates(second, candidate(2L, geometry("POINT(10 10)")));

		RelationCandidateTraversal traversal = new RelationCandidateTraversal(indexer,
				CandidateLookupPolicy.ENVELOPE_INTERSECTS, List.of(first, second), LOG);

		assertTrue(traversal.hasNext());
		traversal.close();

		assertEquals(List.of(first.sourceGeometryLiteral()), indexer.envelopeLookupSources);
		assertEquals(List.of("open:POINT(0 0)", "close:POINT(0 0)"), indexer.events);
		assertFalse(traversal.hasNext());
	}

	private static IndexGeometry geometry(String wkt) {
		return IndexGeometry.fromSourceGeometryLiteral(SourceGeometryLiteral.fromWkt(wkt));
	}

	private static CandidateEntity candidate(long entityId, IndexGeometry... geometries) {
		LinkedHashSet<SourceGeometryLiteral> sources = new LinkedHashSet<>();
		for (IndexGeometry geometry : geometries) {
			sources.add(geometry.sourceGeometryLiteral());
		}
		return new CandidateEntity(entityId, sources);
	}

	private static final class TrackingGeoSparqlIndexer implements GeoSparqlIndexer {
		private final Map<SourceGeometryLiteral, List<CandidateEntity>> envelopeCandidates = new LinkedHashMap<>();
		private final List<SourceGeometryLiteral> envelopeLookupSources = new ArrayList<>();
		private final List<String> events = new ArrayList<>();
		private List<CandidateEntity> fullScanCandidates = List.of();
		private TrackingIterator<CandidateEntity> fullScanIterator;
		private int fullScanLookupCount;

		private void addEnvelopeCandidates(IndexGeometry bound, CandidateEntity... candidates) {
			envelopeCandidates.put(bound.sourceGeometryLiteral(), List.of(candidates));
		}

		@Override
		public CloseableIterator<CandidateEntity> getEnvelopeIntersections(IndexGeometry boundSourceIndexGeometry) {
			SourceGeometryLiteral source = boundSourceIndexGeometry.sourceGeometryLiteral();
			envelopeLookupSources.add(source);
			events.add("open:" + source.lexicalForm());
			return new TrackingIterator<>(envelopeCandidates.getOrDefault(source, List.of()),
					() -> events.add("close:" + source.lexicalForm()));
		}

		@Override
		public CloseableIterator<CandidateEntity> getAllEntities() {
			fullScanLookupCount++;
			fullScanIterator = new TrackingIterator<>(fullScanCandidates, () -> {
			});
			return fullScanIterator;
		}

		@Override
		public CloseableIterator<SourceGeometryLiteral> getSourceGeometryLiteralsFor(long subject) {
			return new TrackingIterator<>(List.of(), () -> {
			});
		}

		@Override
		public void initialize() {
		}

		@Override
		public void indexGeometryList(long subject, Function<Long, String> subjectMapper,
				List<IndexGeometry> geometries) {
		}

		@Override
		public void initSettings() {
		}

		@Override
		public void begin() {
		}

		@Override
		public void commit() {
		}

		@Override
		public void rollback() {
		}

		@Override
		public void appendGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry) {
		}

		@Override
		public void freshIndex() {
		}
	}

	private static final class TrackingIterator<T> implements CloseableIterator<T> {
		private final Iterator<T> values;
		private final Runnable onClose;
		private boolean closed;

		private TrackingIterator(Collection<T> values, Runnable onClose) {
			this.values = values.iterator();
			this.onClose = onClose;
		}

		@Override
		public boolean hasNext() {
			return values.hasNext();
		}

		@Override
		public T next() {
			return values.next();
		}

		@Override
		public void close() {
			if (!closed) {
				closed = true;
				onClose.run();
			}
		}
	}
}
