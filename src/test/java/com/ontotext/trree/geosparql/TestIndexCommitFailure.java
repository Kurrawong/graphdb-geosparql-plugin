package com.ontotext.trree.geosparql;

import com.ontotext.trree.OwlimSchemaRepository;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import com.ontotext.trree.sdk.PluginException;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class TestIndexCommitFailure extends AbstractGeoSparqlPluginTest {
	@Test
	public void testIndexCommitFailureAbortsRdfTransaction() {
		enablePlugin();

		GeoSparqlPlugin plugin = (GeoSparqlPlugin) ((OwlimSchemaRepository) ((SailRepository) repository).getSail())
				.getPlugin("GeoSPARQL");
		IOException commitFailure = new IOException("Simulated index commit failure");
		FailingCommitIndexer indexer = new FailingCommitIndexer(commitFailure);
		plugin.indexer = indexer;

		IRI geometry = VF.createIRI("http://example.com/geometry");
		Literal sourceGeometryLiteral = VF.createLiteral("POINT(1 2)", GeoConstants.GEO_WKT_LITERAL);
		connection.begin();
		connection.add(geometry, GeoConstants.GEO_AS_WKT, sourceGeometryLiteral);

		RepositoryException repositoryException = assertThrows(RepositoryException.class, connection::commit);
		PluginException pluginException = findCause(repositoryException, PluginException.class);
		assertEquals("Unable to commit the GeoSPARQL Lucene index.", pluginException.getMessage());
		assertSame(commitFailure, pluginException.getCause());
		assertEquals(1, indexer.indexedGeometryLists);
		assertEquals(1, indexer.rollbackCount);

		try (RepositoryConnection readConnection = repository.getConnection()) {
			assertFalse(readConnection.hasStatement(geometry, GeoConstants.GEO_AS_WKT, sourceGeometryLiteral, false));
		}
	}

	private static <T extends Throwable> T findCause(Throwable throwable, Class<T> causeType) {
		Throwable cause = throwable;
		while (cause != null) {
			if (causeType.isInstance(cause)) {
				return causeType.cast(cause);
			}
			cause = cause.getCause();
		}
		throw new AssertionError("Expected cause of type " + causeType.getName(), throwable);
	}

	private static final class FailingCommitIndexer implements GeoSparqlIndexer {
		private final IOException commitFailure;
		private int indexedGeometryLists;
		private int rollbackCount;

		private FailingCommitIndexer(IOException commitFailure) {
			this.commitFailure = commitFailure;
		}

		@Override
		public void initialize() {
		}

		@Override
		public void indexGeometryList(long subject, Function<Long, String> subjectMapper,
				List<IndexGeometry> geometries) {
			indexedGeometryLists++;
		}

		@Override
		public CloseableIterator<CandidateEntity> getMatchingEntities(Geometry geometry,
				CandidateLookupPolicy candidateLookupPolicy) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CloseableIterator<CandidateEntity> getAllEntities() {
			throw new UnsupportedOperationException();
		}

		@Override
		public CloseableIterator<IndexGeometry> getGeometriesFor(long subject) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void initSettings() {
		}

		@Override
		public void begin() {
		}

		@Override
		public void commit() throws IOException {
			throw commitFailure;
		}

		@Override
		public void rollback() {
			rollbackCount++;
		}

		@Override
		public void indexGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void freshIndex() {
			throw new UnsupportedOperationException();
		}
	}
}
