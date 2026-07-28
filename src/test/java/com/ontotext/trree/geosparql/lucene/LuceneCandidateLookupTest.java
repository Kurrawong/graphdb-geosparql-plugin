package com.ontotext.trree.geosparql.lucene;

import com.ontotext.test.TemporaryLocalFolder;
import com.ontotext.trree.geosparql.CandidateEntity;
import com.ontotext.trree.geosparql.CloseableIterator;
import com.ontotext.trree.geosparql.GeoSparqlConfig;
import com.ontotext.trree.geosparql.GeoSparqlPlugin;
import com.ontotext.trree.geosparql.GeoSparqlPropertyRelation;
import com.ontotext.trree.geosparql.TestIndexGeometries;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LuceneCandidateLookupTest {
	@Rule
	public TemporaryLocalFolder tmpFolder = new TemporaryLocalFolder();

	@Test
	public void envelopeLookupReturnsIntersectionsAndPrunesSeparatedEnvelopes() throws Exception {
		LuceneGeoIndexer indexer = createIndexer("basic-envelope-candidates");
		IndexGeometry bound = geometry("POLYGON((0 0,0 4,4 4,4 0,0 0))");

		indexer.begin();
		indexer.indexGeometryList(1L, id -> "inside", List.of(geometry("POINT(1 1)")));
		indexer.indexGeometryList(2L, id -> "overlap",
				List.of(geometry("POLYGON((3 3,3 6,6 6,6 3,3 3))")));
		indexer.indexGeometryList(3L, id -> "separated", List.of(geometry("POINT(20 20)")));
		indexer.commit();

		assertArrayEquals(new long[]{1L, 2L},
				collectEntityIds(indexer.getEnvelopeIntersections(bound)));
	}

	@Test
	public void wideAntimeridianEnvelopeRetainsMidpointCandidate() throws Exception {
		LuceneGeoIndexer indexer = createIndexer("antimeridian-envelope-candidates");
		IndexGeometry bound = geometry("LINESTRING(170 10,-170 12)");

		indexer.begin();
		indexer.indexGeometryList(1L, id -> "midpoint", List.of(geometry("POINT(0 11)")));
		indexer.indexGeometryList(2L, id -> "outside latitude", List.of(geometry("POINT(0 20)")));
		indexer.commit();

		assertArrayEquals(new long[]{1L},
				collectEntityIds(indexer.getEnvelopeIntersections(bound)));
	}

	@Test
	public void envelopeLookupAllowsExpectedTopologyFalsePositives() throws Exception {
		List<FalsePositiveFixture> fixtures = List.of(
				new FalsePositiveFixture(
						"POLYGON((0 0,0 10,3 10,3 3,10 3,10 0,0 0))", "POINT(8 8)"),
				new FalsePositiveFixture(
						"POLYGON((0 0,0 10,10 10,10 0,0 0),(4 4,6 4,6 6,4 6,4 4))",
						"POINT(5 5)"),
				new FalsePositiveFixture(
						"MULTIPOLYGON(((0 0,0 2,2 2,2 0,0 0)),"
								+ "((8 8,8 10,10 10,10 8,8 8)))", "POINT(5 5)"),
				new FalsePositiveFixture(
						"GEOMETRYCOLLECTION(POINT(0 0),POINT(10 10))", "POINT(5 5)"));

		for (int i = 0; i < fixtures.size(); i++) {
			FalsePositiveFixture fixture = fixtures.get(i);
			LuceneGeoIndexer indexer = createIndexer("false-positive-" + i);
			IndexGeometry indexed = geometry(fixture.indexedWkt);
			IndexGeometry bound = geometry(fixture.boundWkt);

			assertFalse(GeoSparqlPropertyRelation.SF_INTERSECTS.evaluate(
					indexed.sourceGeometryLiteral(), bound.sourceGeometryLiteral()));

			indexer.begin();
			indexer.indexGeometryList(1L, id -> "false positive", List.of(indexed));
			indexer.commit();

			assertArrayEquals("fixture " + i, new long[]{1L},
					collectEntityIds(indexer.getEnvelopeIntersections(bound)));
		}
	}

	@Test
	public void fullScanKeepsOverlappingEnvelopeDisjointCandidatesDiscoverable() throws Exception {
		LuceneGeoIndexer indexer = createIndexer("disjoint-full-scan");
		IndexGeometry holedPolygon = geometry(
				"POLYGON((0 0,0 10,10 10,10 0,0 0),(4 4,6 4,6 6,4 6,4 4))");
		IndexGeometry pointInHole = geometry("POINT(5 5)");

		assertTrue(GeoSparqlPropertyRelation.SF_DISJOINT.evaluate(
				pointInHole.sourceGeometryLiteral(), holedPolygon.sourceGeometryLiteral()));

		indexer.begin();
		indexer.indexGeometryList(1L, id -> "point in hole", List.of(pointInHole));
		indexer.commit();

		assertArrayEquals(new long[]{1L}, collectEntityIds(indexer.getAllEntities()));
	}

	@Test
	public void emptySentinelsAreAbsentFromEnvelopeQueriesAndPresentInFullScans() throws Exception {
		LuceneGeoIndexer indexer = createIndexer("empty-sentinel");
		IndexGeometry empty = geometry("GEOMETRYCOLLECTION EMPTY");
		IndexGeometry point = geometry("POINT(1 1)");

		indexer.begin();
		indexer.indexGeometryList(1L, id -> "point", List.of(point));
		indexer.indexGeometryList(2L, id -> "empty", List.of(empty));
		indexer.commit();

		assertArrayEquals(new long[]{1L}, collectEntityIds(indexer.getEnvelopeIntersections(point)));
		assertArrayEquals(new long[0], collectEntityIds(indexer.getEnvelopeIntersections(empty)));
		assertArrayEquals(new long[]{1L, 2L}, collectEntityIds(indexer.getAllEntities()));
	}

	@Test
	public void oneEnvelopeRepresentsLargeAndNestedCollections() throws Exception {
		StringBuilder wkt = new StringBuilder("GEOMETRYCOLLECTION(GEOMETRYCOLLECTION(POINT(0 0)),");
		for (int i = 1; i < 1500; i++) {
			if (i > 1) {
				wkt.append(',');
			}
			wkt.append("POINT(").append(i % 100).append(' ').append(i / 100).append(')');
		}
		wkt.append(')');
		IndexGeometry collection = geometry(wkt.toString());
		LuceneGeoIndexer indexer = createIndexer("large-collection");

		indexer.begin();
		indexer.indexGeometryList(1L, id -> "edge point", List.of(geometry("POINT(99 14)")));
		indexer.indexGeometryList(2L, id -> "outside", List.of(geometry("POINT(101 14)")));
		indexer.commit();

		assertArrayEquals(new long[]{1L},
				collectEntityIds(indexer.getEnvelopeIntersections(collection)));
	}

	private LuceneGeoIndexer createIndexer(String directoryName) throws Exception {
		Path dataDir = tmpFolder.getRoot().toPath().resolve(directoryName);
		Files.createDirectories(dataDir);

		GeoSparqlPlugin parent = new GeoSparqlPlugin();
		parent.setConfig(new GeoSparqlConfig());
		parent.setLogger(LoggerFactory.getLogger(LuceneCandidateLookupTest.class));
		parent.setDataDir(dataDir.toFile());

		LuceneGeoIndexer indexer = new LuceneGeoIndexer(parent);
		indexer.initialize();
		return indexer;
	}

	private static IndexGeometry geometry(String wkt) {
		return TestIndexGeometries.fromSource(SourceGeometryLiteral.fromWkt(wkt));
	}

	private static long[] collectEntityIds(CloseableIterator<CandidateEntity> iterator) throws IOException {
		try {
			long[] ids = new long[16];
			int size = 0;
			while (iterator.hasNext()) {
				if (size == ids.length) {
					long[] expanded = new long[ids.length * 2];
					System.arraycopy(ids, 0, expanded, 0, ids.length);
					ids = expanded;
				}
				ids[size++] = iterator.next().entityId();
			}
			long[] result = new long[size];
			System.arraycopy(ids, 0, result, 0, size);
			return result;
		} finally {
			iterator.close();
		}
	}

	private static final class FalsePositiveFixture {
		private final String indexedWkt;
		private final String boundWkt;

		private FalsePositiveFixture(String indexedWkt, String boundWkt) {
			this.indexedWkt = indexedWkt;
			this.boundWkt = boundWkt;
		}
	}
}
