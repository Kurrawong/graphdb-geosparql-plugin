package com.ontotext.trree.geosparql.lucene;

import com.ontotext.test.TemporaryLocalFolder;
import com.ontotext.trree.geosparql.CandidateEntity;
import com.ontotext.trree.geosparql.CandidateLookupPolicy;
import com.ontotext.trree.geosparql.CloseableIterator;
import com.ontotext.trree.geosparql.GeoSparqlConfig;
import com.ontotext.trree.geosparql.GeoSparqlPlugin;
import com.ontotext.trree.geosparql.GeoSparqlPropertyRelation;
import com.ontotext.trree.geosparql.TestIndexGeometries;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.lucene.search.BooleanQuery;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LuceneCandidateLookupTest {
	private static final Logger LOG = LoggerFactory.getLogger(LuceneCandidateLookupTest.class);
	private static final String PROJECTED_POINT_WKT =
			"<http://www.opengis.net/def/crs/EPSG/0/32634> POINT(799997.80 4589779.63)";

	@Rule
	public TemporaryLocalFolder tmpFolder = new TemporaryLocalFolder();

	private LuceneGeoIndexer createIndexer(File dataDir) throws Exception {
		GeoSparqlPlugin parent = new GeoSparqlPlugin();
		parent.setConfig(new GeoSparqlConfig());
		parent.setLogger(LOG);
		parent.setDataDir(dataDir);

		LuceneGeoIndexer indexer = new LuceneGeoIndexer(parent);
		indexer.initialize();
		return indexer;
	}

    @Test
    public void relationSpecificPoliciesMapToExpectedLuceneCandidateSets() throws Exception {
        Path dataDir = tmpFolder.getRoot().toPath().resolve("relation-specific-candidates");
        Files.createDirectories(dataDir);

        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
        IndexGeometry insidePoint = TestIndexGeometries.fromWkt("POINT(1 1)");
        IndexGeometry containingPolygon = TestIndexGeometries.fromWkt(
                "POLYGON((-1 -1,-1 4,4 4,4 -1,-1 -1))");
        IndexGeometry equalPolygon = TestIndexGeometries.fromWkt(
                "POLYGON((0 0,0 2,2 2,2 0,0 0))");
        IndexGeometry overlappingPolygon = TestIndexGeometries.fromWkt(
                "POLYGON((1 1,1 3,3 3,3 1,1 1))");
        IndexGeometry disjointPolygon = TestIndexGeometries.fromWkt(
                "POLYGON((20 20,20 21,21 21,21 20,20 20))");
		IndexGeometry emptySentinel = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION EMPTY"));

        indexer.begin();
        indexer.indexGeometryList(1L, id -> "inside point", List.of(insidePoint));
        indexer.indexGeometryList(2L, id -> "containing polygon", List.of(containingPolygon));
        indexer.indexGeometryList(3L, id -> "equal polygon", List.of(equalPolygon));
        indexer.indexGeometryList(4L, id -> "overlapping polygon", List.of(overlappingPolygon));
        indexer.indexGeometryList(5L, id -> "disjoint polygon", List.of(disjointPolygon));
        indexer.indexGeometryList(6L, id -> "empty sentinel", List.of(emptySentinel));
        indexer.commit();

        assertArrayEquals(new long[]{3L}, collectEntityIds(indexer.getCandidatesForSource(
				equalPolygon, CandidateLookupPolicy.EQUALS)));
        assertArrayEquals(new long[]{1L, 2L, 3L, 4L}, collectEntityIds(indexer.getCandidatesForSource(
				equalPolygon, CandidateLookupPolicy.INTERSECTS)));
        assertArrayEquals(new long[]{1L, 3L}, collectEntityIds(indexer.getCandidatesForSource(
				equalPolygon, CandidateLookupPolicy.WITHIN)));
        assertArrayEquals(new long[]{2L, 3L}, collectEntityIds(indexer.getCandidatesForSource(
				equalPolygon, CandidateLookupPolicy.CONTAINS)));
        assertArrayEquals(new long[]{4L}, collectEntityIds(indexer.getCandidatesForSource(
				equalPolygon, CandidateLookupPolicy.OVERLAPS)));
        assertArrayEquals(new long[]{5L, 6L}, collectEntityIds(indexer.getCandidatesForSource(
				equalPolygon, CandidateLookupPolicy.DISJOINT)));
    }

    @Test
    public void relationSpecificPoliciesRetainJenaTruePairsInBothBindingDirections() throws Exception {
        Path dataDir = tmpFolder.getRoot().toPath().resolve("relation-candidate-safety");
        Files.createDirectories(dataDir);
        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
        List<RelationCandidateFixture> fixtures = relationCandidateFixtures();

        indexer.begin();
        long entityId = 100L;
        for (RelationCandidateFixture fixture : fixtures) {
            fixture.subjectEntityId = entityId++;
            fixture.objectEntityId = entityId++;
            indexer.indexGeometryList(fixture.subjectEntityId, id -> "subject " + id,
                    List.of(fixture.subjectGeometry));
            indexer.indexGeometryList(fixture.objectEntityId, id -> "object " + id,
                    List.of(fixture.objectGeometry));
        }
        indexer.commit();

        for (RelationCandidateFixture fixture : fixtures) {
            assertTrue("Fixture must be true under Jena exact evaluation: " + fixture.relation,
                    fixture.relation.evaluate(fixture.subjectGeometry.sourceGeometryLiteral(),
                            fixture.objectGeometry.sourceGeometryLiteral()));

			assertCandidateIncludes(indexer.getCandidatesForSource(fixture.objectGeometry,
                            fixture.relation.getCandidateLookupPolicy()),
                    fixture.subjectEntityId, fixture.relation + " object-bound");
			assertCandidateIncludes(indexer.getCandidatesForSource(fixture.subjectGeometry,
                            fixture.relation.getInverseCandidateLookupPolicy()),
                    fixture.objectEntityId, fixture.relation + " subject-bound");
        }
    }

    @Test
    public void collectionUnionEqualityRemainsDiscoverableInBothBindingDirections() throws Exception {
        SourceGeometryLiteral collectionSource = SourceGeometryLiteral.fromWkt(
                "GEOMETRYCOLLECTION(POINT(0 0),POINT(1 1))");
        SourceGeometryLiteral multiPointSource = SourceGeometryLiteral.fromWkt(
                "MULTIPOINT((0 0),(1 1))");
		IndexGeometry collectionEnvelope = IndexGeometry.fromSourceGeometryLiteral(collectionSource);
        IndexGeometry multiPoint = TestIndexGeometries.fromSource(multiPointSource);
        Path dataDir = tmpFolder.getRoot().toPath().resolve("collection-union-equality-candidate-safety");
        Files.createDirectories(dataDir);
        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());

        assertTrue("Fixture must be equal under Jena complete-collection evaluation",
                GeoSparqlPropertyRelation.SF_EQUALS.evaluate(collectionSource, multiPointSource));

        long collectionEntityId = 1L;
        long multiPointEntityId = 2L;
        indexer.begin();
		indexer.indexGeometryList(collectionEntityId, id -> "collection", List.of(collectionEnvelope));
        indexer.indexGeometryList(multiPointEntityId, id -> "multipoint", List.of(multiPoint));
        indexer.commit();

		assertCandidateIncludes(indexer.getCandidatesForSource(multiPoint,
						GeoSparqlPropertyRelation.SF_EQUALS.getCandidateLookupPolicy()),
				collectionEntityId, "collection equality object-bound");
		assertCandidateIncludes(indexer.getCandidatesForSource(collectionEnvelope,
						GeoSparqlPropertyRelation.SF_EQUALS.getInverseCandidateLookupPolicy()),
                multiPointEntityId, "collection equality subject-bound");
    }

    @Test
    public void ordinaryRelationPolicyRecoveryIsRestrictedToGenericCollections() throws Exception {
        Path dataDir = tmpFolder.getRoot().toPath().resolve("collection-only-candidate-recovery");
        Files.createDirectories(dataDir);
        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
        IndexGeometry boundPoint = TestIndexGeometries.fromWkt("POINT(0 0)");
        IndexGeometry equalOrdinaryPoint = TestIndexGeometries.fromWkt("POINT(0 0)");
        IndexGeometry intersectingOrdinaryPolygon = TestIndexGeometries.fromWkt(
                "POLYGON((-1 -1,-1 1,1 1,1 -1,-1 -1))");
		IndexGeometry genericCollection = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION(POINT(0 0),POINT(2 2))"));
        IndexGeometry nonIntersectingOrdinaryPoint = TestIndexGeometries.fromWkt("POINT(10 10)");
		IndexGeometry nonIntersectingGenericCollection = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION(POINT(20 20),POINT(30 30))"));

        indexer.begin();
        indexer.indexGeometryList(1L, id -> "equal ordinary point", List.of(equalOrdinaryPoint));
        indexer.indexGeometryList(2L, id -> "intersecting ordinary polygon", List.of(intersectingOrdinaryPolygon));
		indexer.indexGeometryList(3L, id -> "generic collection", List.of(genericCollection));
        indexer.indexGeometryList(4L, id -> "non-intersecting ordinary point",
                List.of(nonIntersectingOrdinaryPoint));
        indexer.indexGeometryList(5L, id -> "non-intersecting generic collection",
				List.of(nonIntersectingGenericCollection));
        indexer.commit();

		assertArrayEquals(new long[]{1L, 3L}, collectEntityIds(indexer.getCandidatesForSource(
				boundPoint, CandidateLookupPolicy.EQUALS)));
		assertArrayEquals(new long[]{1L, 3L}, collectEntityIds(indexer.getCandidatesForSource(
				boundPoint, CandidateLookupPolicy.WITHIN)));
		assertArrayEquals(new long[]{1L, 2L, 3L}, collectEntityIds(indexer.getCandidatesForSource(
				boundPoint, CandidateLookupPolicy.CONTAINS)));
		assertArrayEquals(new long[]{3L}, collectEntityIds(indexer.getCandidatesForSource(
				boundPoint, CandidateLookupPolicy.OVERLAPS)));
		assertArrayEquals(new long[]{1L, 2L, 3L}, collectEntityIds(indexer.getCandidatesForSource(
				boundPoint, CandidateLookupPolicy.INTERSECTS)));
	}

    @Test
	public void largeBoundCollectionUsesOneEnvelopeLookup() throws Exception {
		int componentCount = BooleanQuery.getMaxClauseCount() + 1;
        StringBuilder collectionWkt = new StringBuilder("GEOMETRYCOLLECTION(");
        for (int i = 0; i < componentCount; i++) {
            if (i > 0) {
                collectionWkt.append(',');
            }
            collectionWkt.append("POINT(")
                    .append(i % 100).append(' ')
                    .append(i / 100).append(')');
        }
        collectionWkt.append(')');
		IndexGeometry collectionEnvelope = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt(collectionWkt.toString()));

        Path dataDir = tmpFolder.getRoot().toPath().resolve("large-bound-collection-candidates");
        Files.createDirectories(dataDir);
        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
        IndexGeometry firstComponentMatch = TestIndexGeometries.fromWkt("POINT(0 0)");
        int lastComponent = componentCount - 1;
        IndexGeometry lastComponentMatch = TestIndexGeometries.fromWkt(
                "POINT(" + (lastComponent % 100) + " " + (lastComponent / 100) + ")");

        indexer.begin();
        indexer.indexGeometryList(1L, id -> "first component", List.of(firstComponentMatch));
        indexer.indexGeometryList(2L, id -> "last component", List.of(lastComponentMatch));
        indexer.commit();

		assertArrayEquals(new long[]{1L, 2L}, collectEntityIds(indexer.getCandidatesForSource(
				collectionEnvelope, CandidateLookupPolicy.EQUALS)));
	}

	@Test
	public void collinearCollectionUsesLineEnvelopeForCandidateLookup() throws Exception {
		IndexGeometry collectionEnvelope = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION(POINT(0 0),POINT(10 0))"));
		assertEquals("LineString", collectionEnvelope.indexGeometry().getGeometryType());

		Path dataDir = tmpFolder.getRoot().toPath().resolve("line-collection-envelope-candidates");
		Files.createDirectories(dataDir);
		LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
		IndexGeometry pointOnEnvelope = TestIndexGeometries.fromWkt("POINT(5 0)");
		IndexGeometry pointOutsideEnvelope = TestIndexGeometries.fromWkt("POINT(5 1)");

		indexer.begin();
		indexer.indexGeometryList(1L, id -> "point on envelope", List.of(pointOnEnvelope));
		indexer.indexGeometryList(2L, id -> "point outside envelope", List.of(pointOutsideEnvelope));
		indexer.commit();

		assertArrayEquals(new long[]{1L}, collectEntityIds(indexer.getCandidatesForSource(
				collectionEnvelope, CandidateLookupPolicy.INTERSECTS)));
	}

	@Test
	public void sparseCollectionEnvelopeMayReturnSafeFalsePositive() throws Exception {
		SourceGeometryLiteral collectionSource = SourceGeometryLiteral.fromWkt(
				"GEOMETRYCOLLECTION(POINT(0 0),POINT(10 10))");
		SourceGeometryLiteral interiorPointSource = SourceGeometryLiteral.fromWkt("POINT(5 5)");
		IndexGeometry collectionEnvelope = IndexGeometry.fromSourceGeometryLiteral(collectionSource);
		IndexGeometry interiorPoint = IndexGeometry.fromSourceGeometryLiteral(interiorPointSource);
		Path dataDir = tmpFolder.getRoot().toPath().resolve("sparse-collection-envelope-false-positive");
		Files.createDirectories(dataDir);
		LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());

		assertFalse("The point is inside the envelope but disjoint from the complete collection",
				GeoSparqlPropertyRelation.SF_INTERSECTS.evaluate(collectionSource, interiorPointSource));

		indexer.begin();
		indexer.indexGeometryList(1L, id -> "interior point", List.of(interiorPoint));
		indexer.commit();

		assertArrayEquals(new long[]{1L}, collectEntityIds(indexer.getCandidatesForSource(
				collectionEnvelope, CandidateLookupPolicy.INTERSECTS)));
	}

    @Test
    public void collectionUnionContainmentRemainsDiscoverableInBothBindingDirections() throws Exception {
        SourceGeometryLiteral collectionSource = SourceGeometryLiteral.fromWkt(
                "GEOMETRYCOLLECTION(POINT(0 0),POINT(1 1))");
        SourceGeometryLiteral multiPointSource = SourceGeometryLiteral.fromWkt(
                "MULTIPOINT((0 0),(1 1))");
		IndexGeometry collectionEnvelope = IndexGeometry.fromSourceGeometryLiteral(collectionSource);
        IndexGeometry multiPoint = TestIndexGeometries.fromSource(multiPointSource);
        Path dataDir = tmpFolder.getRoot().toPath().resolve("collection-union-containment-candidate-safety");
        Files.createDirectories(dataDir);
        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());

        assertTrue("Fixture must be contained under Jena complete-collection evaluation",
                GeoSparqlPropertyRelation.SF_CONTAINS.evaluate(collectionSource, multiPointSource));

        long collectionEntityId = 1L;
        long multiPointEntityId = 2L;
        indexer.begin();
		indexer.indexGeometryList(collectionEntityId, id -> "collection", List.of(collectionEnvelope));
        indexer.indexGeometryList(multiPointEntityId, id -> "multipoint", List.of(multiPoint));
        indexer.commit();

		assertCandidateIncludes(indexer.getCandidatesForSource(multiPoint,
						GeoSparqlPropertyRelation.SF_CONTAINS.getCandidateLookupPolicy()),
				collectionEntityId, "collection containment object-bound");
		assertCandidateIncludes(indexer.getCandidatesForSource(collectionEnvelope,
						GeoSparqlPropertyRelation.SF_CONTAINS.getInverseCandidateLookupPolicy()),
                multiPointEntityId, "collection containment subject-bound");
    }

    @Test
    public void collectionEqualityRecoveryCoversNestedGmlAndSingleNonEmptyComponentSources() throws Exception {
        String gml = "<gml:MultiGeometry xmlns:gml=\"http://www.opengis.net/gml/3.2\" srsName=\""
                + IndexGeometry.INDEX_CRS
                + "\"><gml:geometryMember><gml:Point><gml:pos>0 0</gml:pos></gml:Point>"
                + "</gml:geometryMember><gml:geometryMember><gml:Point><gml:pos>1 1</gml:pos></gml:Point>"
                + "</gml:geometryMember></gml:MultiGeometry>";
        List<SourceGeometryLiteral> collectionSources = List.of(
                SourceGeometryLiteral.fromWkt(
                        "GEOMETRYCOLLECTION(GEOMETRYCOLLECTION(POINT(0 0)),POINT(1 1))"),
                SourceGeometryLiteral.fromLiteral(SimpleValueFactory.getInstance().createLiteral(
                        gml, GeoConstants.GEO_GML_LITERAL)),
                SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION(POINT EMPTY,POINT(0 0))"));
        List<SourceGeometryLiteral> equivalentOrdinarySources = List.of(
                SourceGeometryLiteral.fromWkt("MULTIPOINT((0 0),(1 1))"),
                SourceGeometryLiteral.fromWkt("MULTIPOINT((0 0),(1 1))"),
                SourceGeometryLiteral.fromWkt("POINT(0 0)"));

        for (int i = 0; i < collectionSources.size(); i++) {
            SourceGeometryLiteral collectionSource = collectionSources.get(i);
            SourceGeometryLiteral ordinarySource = equivalentOrdinarySources.get(i);
            assertTrue("Fixture must be equal under Jena complete-collection evaluation: " + i,
                    GeoSparqlPropertyRelation.SF_EQUALS.evaluate(collectionSource, ordinarySource));

			IndexGeometry collectionEnvelope = IndexGeometry.fromSourceGeometryLiteral(collectionSource);
            IndexGeometry ordinaryGeometry = TestIndexGeometries.fromSource(ordinarySource);
            Path dataDir = tmpFolder.getRoot().toPath().resolve("collection-representation-safety-" + i);
            Files.createDirectories(dataDir);
            LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
            indexer.begin();
			indexer.indexGeometryList(1L, id -> "collection", List.of(collectionEnvelope));
            indexer.indexGeometryList(2L, id -> "ordinary", List.of(ordinaryGeometry));
            indexer.commit();

			assertCandidateIncludes(indexer.getCandidatesForSource(ordinaryGeometry,
							CandidateLookupPolicy.EQUALS), 1L, "collection representation object-bound " + i);
			assertCandidateIncludes(indexer.getCandidatesForSource(collectionEnvelope,
							CandidateLookupPolicy.EQUALS), 2L, "collection representation subject-bound " + i);
        }
    }

    @Test
	public void ordinaryDisjointLookupAdmitsAllCollectionEnvelopes()
			throws Exception {
        Path dataDir = tmpFolder.getRoot().toPath().resolve("ordinary-disjoint-collection-recovery");
        Files.createDirectories(dataDir);
        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
        IndexGeometry boundPolygon = TestIndexGeometries.fromWkt(
                "POLYGON((0 0,0 2,2 2,2 0,0 0))");
        IndexGeometry disjointOrdinary = TestIndexGeometries.fromWkt("POINT(10 10)");
        IndexGeometry intersectingOrdinary = TestIndexGeometries.fromWkt("POINT(1 1)");
		IndexGeometry intersectingCollection = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION(POINT(1 1),POINT(20 20))"));
		IndexGeometry emptyCollection = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION EMPTY"));
		IndexGeometry fullyIntersectingCollection = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION(POINT(0.5 0.5),POINT(1.5 1.5))"));
		IndexGeometry disjointCollection = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION(POINT(20 20),POINT(30 30))"));

        indexer.begin();
        indexer.indexGeometryList(1L, id -> "disjoint ordinary", List.of(disjointOrdinary));
        indexer.indexGeometryList(2L, id -> "intersecting ordinary", List.of(intersectingOrdinary));
		indexer.indexGeometryList(3L, id -> "intersecting collection", List.of(intersectingCollection));
		indexer.indexGeometryList(4L, id -> "empty collection", List.of(emptyCollection));
		indexer.indexGeometryList(5L, id -> "fully intersecting collection", List.of(fullyIntersectingCollection));
		indexer.indexGeometryList(6L, id -> "disjoint collection", List.of(disjointCollection));
        indexer.commit();

		assertArrayEquals(new long[]{1L, 3L, 4L, 5L, 6L}, collectEntityIds(indexer.getCandidatesForSource(
				boundPolygon, CandidateLookupPolicy.DISJOINT)));
	}

	@Test
	public void nonEmptyBoundCollectionDisjointLookupReturnsEverySourceDocument() throws Exception {
        Path dataDir = tmpFolder.getRoot().toPath().resolve("bound-collection-disjoint-prefilter");
        Files.createDirectories(dataDir);
        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
		IndexGeometry boundCollection = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION(POINT(0 0),POINT(10 10))"));
        IndexGeometry disjoint = TestIndexGeometries.fromWkt("POINT(30 30)");
        IndexGeometry intersectsFirstComponent = TestIndexGeometries.fromWkt("POINT(0 0)");
        IndexGeometry intersectsOtherComponent = TestIndexGeometries.fromWkt("POINT(10 10)");
		IndexGeometry emptyCollection = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION EMPTY"));

        indexer.begin();
        indexer.indexGeometryList(1L, id -> "disjoint", List.of(disjoint));
        indexer.indexGeometryList(2L, id -> "intersects chosen component", List.of(intersectsFirstComponent));
        indexer.indexGeometryList(3L, id -> "intersects another component", List.of(intersectsOtherComponent));
        indexer.indexGeometryList(4L, id -> "empty collection", List.of(emptyCollection));
        indexer.commit();

		assertArrayEquals(new long[]{1L, 2L, 3L, 4L}, collectEntityIds(indexer.getCandidatesForSource(
				boundCollection, CandidateLookupPolicy.DISJOINT)));
    }

	@Test
	public void emptyBoundCollectionReturnsNoNonDisjointCandidatesAndAllDisjointCandidates() throws Exception {
		Path dataDir = tmpFolder.getRoot().toPath().resolve("bound-empty-collection-candidates");
		Files.createDirectories(dataDir);
		LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
		IndexGeometry emptyCollection = IndexGeometry.fromSourceGeometryLiteral(
				SourceGeometryLiteral.fromWkt("GEOMETRYCOLLECTION EMPTY"));
		IndexGeometry point = TestIndexGeometries.fromWkt("POINT(0 0)");
		IndexGeometry polygon = TestIndexGeometries.fromWkt(
				"POLYGON((10 10,10 12,12 12,12 10,10 10))");

		indexer.begin();
		indexer.indexGeometryList(1L, id -> "point", List.of(point));
		indexer.indexGeometryList(2L, id -> "polygon", List.of(polygon));
		indexer.indexGeometryList(3L, id -> "empty collection", List.of(emptyCollection));
		indexer.commit();

		for (CandidateLookupPolicy policy : List.of(
				CandidateLookupPolicy.EQUALS,
				CandidateLookupPolicy.INTERSECTS,
				CandidateLookupPolicy.WITHIN,
				CandidateLookupPolicy.CONTAINS,
				CandidateLookupPolicy.OVERLAPS)) {
			assertArrayEquals(policy.name(), new long[0], collectEntityIds(
					indexer.getCandidatesForSource(emptyCollection, policy)));
		}
		assertArrayEquals(new long[]{1L, 2L, 3L}, collectEntityIds(indexer.getCandidatesForSource(
				emptyCollection, CandidateLookupPolicy.DISJOINT)));
	}

    @Test
    public void disjointLookupDoesNotSubtractPrefixTreeFalsePositiveIntersections() throws Exception {
        Path dataDir = tmpFolder.getRoot().toPath().resolve("disjoint-prefix-tree-false-positive");
        Files.createDirectories(dataDir);
        LuceneGeoIndexer indexer = createIndexer(dataDir.toFile());
        IndexGeometry candidate = TestIndexGeometries.fromWkt(
                "LINESTRING(-83.4 34.0,-83.3 34.3)");
        IndexGeometry bound = TestIndexGeometries.fromWkt(
                "POLYGON((-83.2 34.3,-83.0 34.3,-83.0 34.5,-83.2 34.5,-83.2 34.3))");
        assertTrue(candidate.indexGeometry().disjoint(bound.indexGeometry()));

        indexer.begin();
        indexer.indexGeometryList(1L, id -> "candidate", List.of(candidate));
        indexer.commit();

		assertArrayEquals(new long[]{1L}, collectEntityIds(indexer.getCandidatesForSource(
				bound, CandidateLookupPolicy.DISJOINT)));
    }

    private long[] collectEntityIds(CloseableIterator<CandidateEntity> iterator) throws IOException {
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

    private void assertCandidateIncludes(CloseableIterator<CandidateEntity> iterator,
                                         long expectedEntityId, String context) throws IOException {
        long[] candidateIds = collectEntityIds(iterator);
        for (long candidateId : candidateIds) {
            if (candidateId == expectedEntityId) {
                return;
            }
        }
        throw new AssertionError(context + " candidate lookup excluded entity " + expectedEntityId);
    }

    private List<RelationCandidateFixture> relationCandidateFixtures() {
        String equalA = "POLYGON((0 0,0 4,4 4,4 0,0 0))";
        String equalB = "POLYGON((0 0,0 4,4 4,4 0,0 0))";
        String disjointA = "POLYGON((0 0,0 2,2 2,2 0,0 0))";
        String disjointB = "POLYGON((3 3,3 5,5 5,5 3,3 3))";
        String meetA = "POLYGON((0 0,0 2,2 2,2 0,0 0))";
        String meetB = "POLYGON((2 0,2 2,4 2,4 0,2 0))";
        String overlapA = "POLYGON((0 0,0 3,3 3,3 0,0 0))";
        String overlapB = "POLYGON((2 2,2 5,5 5,5 2,2 2))";
        String containsA = "POLYGON((0 0,0 4,4 4,4 0,0 0))";
        String containsB = "POLYGON((1 1,1 2,2 2,2 1,1 1))";
        String insideA = containsB;
        String insideB = containsA;
        String boundaryContainsA = "POLYGON((0 0,0 4,4 4,4 0,0 0))";
        String boundaryContainsB = "POLYGON((0 1,0 2,2 2,2 1,0 1))";
        String boundaryInsideA = boundaryContainsB;
        String boundaryInsideB = boundaryContainsA;
        String crossA = "LINESTRING(0 0,2 2)";
        String crossB = "LINESTRING(0 2,2 0)";

        return List.of(
                fixture(GeoSparqlPropertyRelation.SF_EQUALS, equalA, equalB),
                fixture(GeoSparqlPropertyRelation.SF_DISJOINT, disjointA, disjointB),
                fixture(GeoSparqlPropertyRelation.SF_INTERSECTS, overlapA, overlapB),
                fixture(GeoSparqlPropertyRelation.SF_TOUCHES, meetA, meetB),
                fixture(GeoSparqlPropertyRelation.SF_WITHIN, insideA, insideB),
                fixture(GeoSparqlPropertyRelation.SF_CONTAINS, containsA, containsB),
                fixture(GeoSparqlPropertyRelation.SF_OVERLAPS, overlapA, overlapB),
                fixture(GeoSparqlPropertyRelation.SF_CROSSES, crossA, crossB),
                fixture(GeoSparqlPropertyRelation.EH_EQUALS, equalA, equalB),
                fixture(GeoSparqlPropertyRelation.EH_DISJOINT, disjointA, disjointB),
                fixture(GeoSparqlPropertyRelation.EH_MEET, meetA, meetB),
                fixture(GeoSparqlPropertyRelation.EH_OVERLAP, overlapA, overlapB),
                fixture(GeoSparqlPropertyRelation.EH_COVERS, boundaryContainsA, boundaryContainsB),
                fixture(GeoSparqlPropertyRelation.EH_COVERED_BY, boundaryInsideA, boundaryInsideB),
                fixture(GeoSparqlPropertyRelation.EH_INSIDE, insideA, insideB),
                fixture(GeoSparqlPropertyRelation.EH_CONTAINS, containsA, containsB),
                fixture(GeoSparqlPropertyRelation.RCC8_EQ, equalA, equalB),
                fixture(GeoSparqlPropertyRelation.RCC8_DC, disjointA, disjointB),
                fixture(GeoSparqlPropertyRelation.RCC8_EC, meetA, meetB),
                fixture(GeoSparqlPropertyRelation.RCC8_PO, overlapA, overlapB),
                fixture(GeoSparqlPropertyRelation.RCC8_TPPI, boundaryContainsA, boundaryContainsB),
                fixture(GeoSparqlPropertyRelation.RCC8_TPP, boundaryInsideA, boundaryInsideB),
                fixture(GeoSparqlPropertyRelation.RCC8_NTPP, insideA, insideB),
                fixture(GeoSparqlPropertyRelation.RCC8_NTPPI, containsA, containsB),
                fixture(GeoSparqlPropertyRelation.SF_WITHIN, PROJECTED_POINT_WKT,
                        "<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POLYGON(("
                                + "24.587775547985 41.402595789244,"
                                + "24.589775547985 41.402595789244,"
                                + "24.589775547985 41.404595789244,"
                                + "24.587775547985 41.404595789244,"
                                + "24.587775547985 41.402595789244))"));
    }

    private RelationCandidateFixture fixture(GeoSparqlPropertyRelation relation,
                                             String subjectWkt, String objectWkt) {
        return new RelationCandidateFixture(relation,
                TestIndexGeometries.fromSource(SourceGeometryLiteral.fromWkt(subjectWkt)),
                TestIndexGeometries.fromSource(SourceGeometryLiteral.fromWkt(objectWkt)));
    }

    private static final class RelationCandidateFixture {
        private final GeoSparqlPropertyRelation relation;
        private final IndexGeometry subjectGeometry;
        private final IndexGeometry objectGeometry;
        private long subjectEntityId;
        private long objectEntityId;

        private RelationCandidateFixture(GeoSparqlPropertyRelation relation,
                                         IndexGeometry subjectGeometry, IndexGeometry objectGeometry) {
            this.relation = relation;
            this.subjectGeometry = subjectGeometry;
            this.objectGeometry = objectGeometry;
        }
    }

}
