package com.ontotext.trree.geosparql;

import com.ontotext.test.TemporaryLocalFolder;
import com.ontotext.trree.geosparql.jena.CandidateBoundsKind;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.lucene.LuceneGeoIndexer;
import com.ontotext.trree.sdk.Entities;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Compares index-backed GeoSPARQL property relations with direct Jena/JTS evaluation.
 *
 * <p>The candidate invariant is {@code exact = true ⇒ index-backed = true} for every GeoSPARQL property relation
 * family. That is the contract the selective SIS candidate envelope must satisfy. Disjoint envelope proofs are also
 * checked against exact evaluation.
 */
public class GeoSparqlCandidateEnvelopeDifferentialTest {
	private static final long PREDICATE = 200L;
	private static final String GDA2020 = "http://www.opengis.net/def/crs/EPSG/0/7844";
	private static final String MGA56 = "http://www.opengis.net/def/crs/EPSG/0/7856";
	private static final String MGA55 = "http://www.opengis.net/def/crs/EPSG/0/7855";
	private static final String EPSG_4326 = "http://www.opengis.net/def/crs/EPSG/0/4326";
	private static final String UTM_32N = "http://www.opengis.net/def/crs/EPSG/0/32632";
	private static final String UTM_34N = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String WEB_MERCATOR = "http://www.opengis.net/def/crs/EPSG/0/3857";
	private static final String LAMBERT_93 = "http://www.opengis.net/def/crs/EPSG/0/2154";
	private static final String WGS84_3D = "http://www.opengis.net/def/crs/EPSG/0/4979";
	private static final String GDA2020_3D = "http://www.opengis.net/def/crs/EPSG/0/7843";
	private static final Set<GeoSparqlPropertyRelation> DISJOINT_RELATIONS = EnumSet.of(
			GeoSparqlPropertyRelation.SF_DISJOINT,
			GeoSparqlPropertyRelation.EH_DISJOINT,
			GeoSparqlPropertyRelation.RCC8_DC);

	@Rule
	public TemporaryLocalFolder tmpFolder = new TemporaryLocalFolder();

	@Before
	public void initializeAdapter() {
		com.ontotext.trree.geosparql.jena.JenaGeometryAdapter.initialize();
	}

	@Test
	public void indexBackedRelationsDoNotOmitExactMatchesAcrossFamiliesAndCrs() throws Exception {
		Fixture fixture = createFixture("candidate-envelope-differential", representativeGeometries());

		for (GeoSparqlPropertyRelation relation : GeoSparqlPropertyRelation.values()) {
			for (long boundEntityId : fixture.sources.keySet()) {
			assertTrue(relation + " object-bound entity " + boundEntityId + " omitted exact matches",
					runIndexed(fixture, relation, 0, boundEntityId)
							.containsAll(runReference(fixture, relation, 0, boundEntityId)));
			assertTrue(relation + " subject-bound entity " + boundEntityId + " omitted exact matches",
					runIndexed(fixture, relation, boundEntityId, 0)
							.containsAll(runReference(fixture, relation, boundEntityId, 0)));
			}
		}
	}

	@Test
	public void definiteDisjointEnvelopeMatchesAreExactDisjoint() throws Exception {
		Fixture fixture = createFixture("definite-disjoint-proofs", representativeGeometries());
		for (GeoSparqlPropertyRelation relation : DISJOINT_RELATIONS) {
			for (Map.Entry<Long, List<IndexGeometry>> boundEntry : fixture.sources.entrySet()) {
				assertDefiniteMatchesAreExact(fixture, relation, boundEntry.getKey(), boundEntry.getValue());
			}
		}
	}

	@Test
	public void threeDimensionalGeographicCrsMatchesCrs84And4326ThroughTheIndex() throws Exception {
		Map<Long, List<IndexGeometry>> sources = new LinkedHashMap<>();
		sources.put(1L, geometries("<" + WGS84_3D + "> POINT Z(-27.47 153.03 55)"));
		sources.put(2L, geometries("POINT(153.03 -27.47)"));
		sources.put(3L, geometries("<" + EPSG_4326 + "> POLYGON((-28 152,-27 152,-27 154,-28 154,-28 152))"));
		Fixture fixture = createFixture("wgs84-3d-crs-differential", sources);

		assertEquals(CandidateBoundsKind.TRANSFORMED, sources.get(1L).get(0).candidateBoundsKind());
		assertTrue(GeoSparqlPropertyRelation.SF_INTERSECTS.evaluate(
				sources.get(1L).get(0).sourceGeometryLiteral(),
				sources.get(2L).get(0).sourceGeometryLiteral()));
		assertTrue(GeoSparqlPropertyRelation.SF_INTERSECTS.evaluate(
				sources.get(1L).get(0).sourceGeometryLiteral(),
				sources.get(3L).get(0).sourceGeometryLiteral()));
		// Bind the 3D literal as the relation subject so Jena evaluates in the 3D CRS. Reducing EPSG:4979
		// to CRS84 or EPSG:4326 produces a NaN ordinate that Jena precision cleanup rejects.
		assertEquals(Set.of(1L, 2L, 3L),
				runIndexed(fixture, GeoSparqlPropertyRelation.SF_INTERSECTS, 1L, 0));
		assertEquals(Set.of(1L, 2L, 3L),
				runReference(fixture, GeoSparqlPropertyRelation.SF_INTERSECTS, 1L, 0));
	}

	@Test
	public void gda2020ThreeDimensionalCrsMatchesGda2020ThroughTheIndex() throws Exception {
		Map<Long, List<IndexGeometry>> sources = new LinkedHashMap<>();
		sources.put(1L, geometries("<" + GDA2020_3D + "> POINT Z(-27.47 153.03 55)"));
		sources.put(2L, geometries(
				"<" + GDA2020 + "> POLYGON((-27.6 152.9,-27.3 152.9,-27.3 153.2,-27.6 153.2,-27.6 152.9))"));
		Fixture fixture = createFixture("gda2020-3d-crs-differential", sources);

		assertEquals(CandidateBoundsKind.TRANSFORMED, sources.get(1L).get(0).candidateBoundsKind());
		assertTrue(GeoSparqlPropertyRelation.SF_INTERSECTS.evaluate(
				sources.get(1L).get(0).sourceGeometryLiteral(),
				sources.get(2L).get(0).sourceGeometryLiteral()));
		assertEquals(Set.of(1L, 2L),
				runIndexed(fixture, GeoSparqlPropertyRelation.SF_INTERSECTS, 1L, 0));
		assertEquals(Set.of(1L, 2L),
				runReference(fixture, GeoSparqlPropertyRelation.SF_INTERSECTS, 1L, 0));
	}

	@Test
	public void multiGeometryEntityMatchesWhenAnySourcePairHolds() throws Exception {
		Map<Long, List<IndexGeometry>> sources = new LinkedHashMap<>();
		sources.put(1L, geometries("POINT(-40 -10)", "POINT(5 5)"));
		sources.put(2L, geometries("POLYGON((0 0,0 10,10 10,10 0,0 0))"));
		sources.put(3L, geometries("POINT(20 20)"));
		Fixture fixture = createFixture("multi-geometry-entity-differential", sources);

		assertEquals(Set.of(1L, 2L),
				runIndexed(fixture, GeoSparqlPropertyRelation.SF_INTERSECTS, 0, 2L));
		assertEquals(Set.of(1L, 2L),
				runReference(fixture, GeoSparqlPropertyRelation.SF_INTERSECTS, 0, 2L));
		assertFalse(GeoSparqlPropertyRelation.SF_INTERSECTS.evaluate(
				sources.get(1L).get(0).sourceGeometryLiteral(),
				sources.get(2L).get(0).sourceGeometryLiteral()));
		assertTrue(GeoSparqlPropertyRelation.SF_INTERSECTS.evaluate(
				sources.get(1L).get(1).sourceGeometryLiteral(),
				sources.get(2L).get(0).sourceGeometryLiteral()));
	}

	@Test
	public void envelopeContainedNonMatchesAreNotExactDisjoint() throws Exception {
		Map<Long, List<IndexGeometry>> sources = new LinkedHashMap<>();
		sources.put(100L, geometries("POLYGON((0 0,0 10,10 10,10 0,0 0))"));
		sources.put(1L, geometries("POINT(5 5)"));
		sources.put(2L, geometries("POLYGON((2 2,2 4,4 4,4 2,2 2))"));
		sources.put(3L, geometries("POINT(20 20)"));
		Fixture fixture = createFixture("definite-non-match-proofs", sources);
		IndexGeometry bound = sources.get(100L).get(0);

		assertTrue(bound.isEnvelopeCoveringRectangle());
		assertFalse(GeoSparqlPropertyRelation.SF_DISJOINT.evaluate(
				bound.sourceGeometryLiteral(), sources.get(1L).get(0).sourceGeometryLiteral()));
		assertFalse(GeoSparqlPropertyRelation.SF_DISJOINT.evaluate(
				bound.sourceGeometryLiteral(), sources.get(2L).get(0).sourceGeometryLiteral()));
		assertEquals(Set.of(3L), runIndexed(fixture, GeoSparqlPropertyRelation.SF_DISJOINT, 0, 100L));
		assertEquals(Set.of(3L), runIndexed(fixture, GeoSparqlPropertyRelation.EH_DISJOINT, 0, 100L));
		assertEquals(Set.of(), runIndexed(fixture, GeoSparqlPropertyRelation.RCC8_DC, 0, 100L));
	}

	private void assertDefiniteMatchesAreExact(Fixture fixture, GeoSparqlPropertyRelation relation,
			long boundEntityId, List<IndexGeometry> boundGeometries) {
		try (RelationCandidateTraversal traversal = new RelationCandidateTraversal(
				fixture.plugin.indexer, relation, boundGeometries, LoggerFactory.getLogger(getClass()))) {
			while (traversal.hasNext()) {
				RelationCandidateTraversal.Candidate candidate = traversal.next();
				if (candidate.matchCertainty() != RelationCandidateTraversal.MatchCertainty.DEFINITE_MATCH) {
					continue;
				}
				List<SourceGeometryLiteral> candidateSources = fixture.sources.get(candidate.entityId())
						.stream()
						.map(IndexGeometry::sourceGeometryLiteral)
						.toList();
				List<SourceGeometryLiteral> boundSources = boundGeometries.stream()
						.map(IndexGeometry::sourceGeometryLiteral)
						.toList();
				Boolean exact = exactRelationOrUnknown(relation, candidateSources, boundSources);
				if (exact == null) {
					exact = exactRelationOrUnknown(relation, boundSources, candidateSources);
				}
				if (Boolean.FALSE.equals(exact)) {
					fail(relation + " definite match " + candidate.entityId() + " vs " + boundEntityId
							+ " is not exact disjoint");
				}
			}
		}
	}

	private Map<Long, List<IndexGeometry>> representativeGeometries() {
		Map<Long, List<IndexGeometry>> sources = new LinkedHashMap<>();
		sources.put(1L, geometries(
				"<" + GDA2020 + "> POLYGON((-27.6 152.9,-27.3 152.9,-27.3 153.2,-27.6 153.2,-27.6 152.9))"));
		sources.put(2L, geometries("POINT(153.03 -27.47)"));
		sources.put(3L, geometries("<" + MGA56 + "> POINT(502890 6959800)"));
		sources.put(4L, geometries("<" + MGA55 + "> POINT(500000 5800000)"));
		sources.put(5L, geometries("<" + EPSG_4326 + "> POLYGON((-28 152,-27 152,-27 154,-28 154,-28 152))"));
		sources.put(6L, geometries("<" + UTM_34N + "> LINESTRING(200000 7000000, 800000 7000000)"));
		sources.put(7L, geometries("<" + UTM_34N + "> POINT(500000 7000000)"));
		sources.put(8L, geometries("<" + UTM_32N + "> POINT(500000 5200000)"));
		sources.put(9L, geometries(
				"<" + WEB_MERCATOR + "> POLYGON((17000000 -3200000,17000000 -3100000,17100000 -3100000,17100000 -3200000,17000000 -3200000))"));
		sources.put(10L, geometries("<" + LAMBERT_93 + "> POINT(650000 6860000)"));
		sources.put(11L, geometries("POINT(-40 -10)"));
		sources.put(12L, geometries("POLYGON((0 0,0 10,10 10,10 0,0 0))"));
		sources.put(13L, geometries("POINT(5 5)"));
		sources.put(14L, geometries("POINT(20 20)"));
		sources.put(15L, geometries("GEOMETRYCOLLECTION(POINT(153.03 -27.47),LINESTRING(152.9 -27.6,153.2 -27.3))"));
		sources.put(16L, geometries("<" + MGA56 + "> POINT Z(502890 6959800 40)"));
		sources.put(17L, geometries("GEOMETRYCOLLECTION EMPTY"));
		sources.put(18L, geometries("POINT(-40 -10)", "<" + GDA2020 + "> POINT(-27.47 153.03)"));
		return sources;
	}

	private Fixture createFixture(String directoryName, Map<Long, List<IndexGeometry>> sources)
			throws Exception {
		Path dataDir = tmpFolder.getRoot().toPath().resolve(directoryName);
		Files.createDirectories(dataDir);

		GeoSparqlPlugin plugin = new GeoSparqlPlugin();
		GeoSparqlConfig config = new GeoSparqlConfig();
		config.updateCurrentSettings();
		plugin.setConfig(config);
		plugin.setLogger(LoggerFactory.getLogger(GeoSparqlCandidateEnvelopeDifferentialTest.class));
		plugin.setDataDir(dataDir.toFile());

		LuceneGeoIndexer indexer = new LuceneGeoIndexer(plugin);
		indexer.initialize();
		plugin.indexer = indexer;

		indexer.begin();
		for (Map.Entry<Long, List<IndexGeometry>> entry : sources.entrySet()) {
			indexer.indexGeometryList(entry.getKey(), id -> "entity-" + id, entry.getValue());
		}
		indexer.commit();

		FakeEntities entities = new FakeEntities();
		for (Long entityId : sources.keySet()) {
			entities.add(entityId, SimpleValueFactory.getInstance()
					.createIRI("http://example.com/entity/" + entityId));
		}
		return new Fixture(plugin, entities, Map.copyOf(sources));
	}

	private Set<Long> runIndexed(Fixture fixture, GeoSparqlPropertyRelation relation,
			long subject, long object) {
		List<Long> rows = collectCandidateIds(new GeoSparqlRelationIterator(fixture.plugin, relation,
				subject, PREDICATE, object, fixture.entities), subject);
		return new LinkedHashSet<>(rows);
	}

	private Set<Long> runReference(Fixture fixture, GeoSparqlPropertyRelation relation,
			long subject, long object) throws Exception {
		long boundEntityId = subject == 0 ? object : subject;
		List<SourceGeometryLiteral> boundSources = fixture.sources
				.getOrDefault(boundEntityId, List.of())
				.stream()
				.map(IndexGeometry::sourceGeometryLiteral)
				.toList();
		Set<Long> matchingEntityIds = new LinkedHashSet<>();
		CloseableIterator<CandidateEntity> candidates = fixture.plugin.indexer.getAllEntities();
		try {
			while (candidates.hasNext()) {
				CandidateEntity candidate = candidates.next();
				List<SourceGeometryLiteral> candidateSources = candidate.matchingSourceGeometryLiterals();
				boolean holds = subject == 0
						? Boolean.TRUE.equals(exactRelationOrUnknown(relation, candidateSources, boundSources))
						: Boolean.TRUE.equals(exactRelationOrUnknown(relation, boundSources, candidateSources));
				if (holds) {
					matchingEntityIds.add(candidate.entityId());
				}
			}
		} finally {
			candidates.close();
		}
		return matchingEntityIds;
	}

	private Boolean exactRelationOrUnknown(GeoSparqlPropertyRelation relation,
			List<SourceGeometryLiteral> subjectSources, List<SourceGeometryLiteral> objectSources) {
		boolean evaluated = false;
		for (SourceGeometryLiteral subjectSource : subjectSources) {
			for (SourceGeometryLiteral objectSource : objectSources) {
				try {
					if (relation.evaluate(subjectSource, objectSource)) {
						return true;
					}
					evaluated = true;
				} catch (com.ontotext.trree.geosparql.jena.JenaGeoSparqlException ignored) {
					// An unevaluable pair must not hide a later evaluable true match.
				}
			}
		}
		return evaluated ? false : null;
	}

	private List<Long> collectCandidateIds(GeoSparqlRelationIterator iterator, long boundSubject) {
		try {
			List<Long> entityIds = new ArrayList<>();
			while (iterator.next()) {
				entityIds.add(boundSubject == 0 ? iterator.subject : iterator.object);
			}
			return entityIds;
		} finally {
			iterator.close();
		}
	}

	private static List<IndexGeometry> geometries(String... wkts) {
		return java.util.Arrays.stream(wkts).map(TestIndexGeometries::fromWkt).toList();
	}

	private record Fixture(GeoSparqlPlugin plugin, FakeEntities entities,
			Map<Long, List<IndexGeometry>> sources) {
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
