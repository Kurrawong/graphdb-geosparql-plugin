package com.ontotext.trree.geosparql;

import com.ontotext.trree.OwlimSchemaRepository;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.lucene.LuceneGeoIndexer;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import com.ontotext.trree.sdk.PluginException;
import org.apache.lucene.store.AlreadyClosedException;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TestIndexCommitFailure extends AbstractGeoSparqlPluginTest {
	@Test
	public void completionFailureLeavesCommittedRepositoryAndIndexFailClosed() throws Exception {
		enablePlugin();
		GeoSparqlPlugin plugin = geoSparqlPlugin();
		FailingCompletionIndexer indexer = new FailingCompletionIndexer(plugin);
		indexer.initialize();
		plugin.indexer = indexer;

		IRI geometry = VF.createIRI("http://example.com/completion-failure-geometry");
		Literal sourceGeometryLiteral = VF.createLiteral("{\"type\":\"Point\",\"coordinates\":[1,2]}",
				GeoConstants.GEO_JSON_LITERAL);
		indexer.failCompletion = true;
		connection.begin();
		connection.add(geometry, GeoConstants.GEO_AS_GEO_JSON, sourceGeometryLiteral);
		connection.commit();

		assertTrue(connection.hasStatement(geometry, GeoConstants.GEO_AS_GEO_JSON, sourceGeometryLiteral, false));
		assertFalse(indexer.isTransactionActive());
		assertPendingTransactionFailure(indexer);
		LuceneGeoIndexer restarted = new LuceneGeoIndexer(plugin);
		restarted.initialize();
		assertPendingTransactionFailure(restarted);
	}

	@Test
	public void rollbackFailureAfterRejectedCommitLeavesIndexFailClosed() throws Exception {
		enablePlugin();
		GeoSparqlPlugin plugin = geoSparqlPlugin();
		FailingPublishedCommitRollbackIndexer indexer = new FailingPublishedCommitRollbackIndexer(plugin);
		indexer.initialize();
		plugin.indexer = indexer;

		IRI geometry = VF.createIRI("http://example.com/rollback-failure-geometry");
		Literal original = VF.createLiteral("{\"type\":\"Point\",\"coordinates\":[1,2]}",
				GeoConstants.GEO_JSON_LITERAL);
		Literal replacement = VF.createLiteral("{\"type\":\"Point\",\"coordinates\":[3,4]}",
				GeoConstants.GEO_JSON_LITERAL);
		connection.begin();
		connection.add(geometry, GeoConstants.GEO_AS_GEO_JSON, original);
		connection.commit();

		indexer.failCommitAfterPublish = true;
		indexer.failRestore = true;
		connection.begin();
		connection.remove(geometry, GeoConstants.GEO_AS_GEO_JSON, original);
		connection.add(geometry, GeoConstants.GEO_AS_GEO_JSON, replacement);
		RepositoryException commitFailure = assertThrows(RepositoryException.class, connection::commit);

		assertSame(indexer.commitFailure, findCause(commitFailure, IOException.class));
		assertTrue(connection.hasStatement(geometry, GeoConstants.GEO_AS_GEO_JSON, original, false));
		assertFalse(connection.hasStatement(geometry, GeoConstants.GEO_AS_GEO_JSON, replacement, false));
		assertFalse(indexer.isTransactionActive());
		assertPendingTransactionFailure(indexer);
		LuceneGeoIndexer restarted = new LuceneGeoIndexer(plugin);
		restarted.initialize();
		assertPendingTransactionFailure(restarted);
	}

	@Test
	public void ignoreErrorsDoesNotHideIndexCommitFailure() {
		enablePlugin();
		executePluginControl(GeoSparqlPlugin.IGNORE_ERRORS_PREDICATE_IRI, VF.createLiteral(true));

		GeoSparqlPlugin plugin = geoSparqlPlugin();
		IOException commitFailure = new IOException("Simulated index commit failure");
		FailingCommitIndexer indexer = new FailingCommitIndexer(commitFailure);
		plugin.indexer = indexer;

		IRI geometry = VF.createIRI("http://example.com/geometry");
		Literal sourceGeometryLiteral = VF.createLiteral("{\"type\":\"Point\",\"coordinates\":[1,2]}",
				GeoConstants.GEO_JSON_LITERAL);
		connection.begin();
		connection.add(geometry, GeoConstants.GEO_AS_GEO_JSON, sourceGeometryLiteral);

		RepositoryException repositoryException = assertThrows(RepositoryException.class, connection::commit);
		PluginException pluginException = findCause(repositoryException, PluginException.class);
		assertEquals("Unable to commit the GeoSPARQL Lucene index.", pluginException.getMessage());
		assertSame(commitFailure, pluginException.getCause());
		assertEquals(1, indexer.indexedGeometryLists);
		assertEquals(1, indexer.rollbackCount);

		try (RepositoryConnection readConnection = repository.getConnection()) {
			assertFalse(readConnection.hasStatement(geometry, GeoConstants.GEO_AS_GEO_JSON,
					sourceGeometryLiteral, false));
		}
	}

	@Test
	public void ignoreErrorsDoesNotHideIncrementalIndexWriteFailure() {
		enablePlugin();
		executePluginControl(GeoSparqlPlugin.IGNORE_ERRORS_PREDICATE_IRI, VF.createLiteral(true));

		IRI geometry = VF.createIRI("http://example.com/geometry");
		Literal original = VF.createLiteral("{\"type\":\"Point\",\"coordinates\":[1,2]}",
				GeoConstants.GEO_JSON_LITERAL);
		Literal replacement = VF.createLiteral("{\"type\":\"Point\",\"coordinates\":[3,4]}",
				GeoConstants.GEO_JSON_LITERAL);
		connection.begin();
		connection.add(geometry, GeoConstants.GEO_AS_GEO_JSON, original);
		connection.commit();

		GeoSparqlPlugin plugin = geoSparqlPlugin();
		IOException writeFailure = new IOException("Simulated Lucene addDocuments failure");
		FailingIndexGeometryListIndexer indexer = new FailingIndexGeometryListIndexer(writeFailure);
		plugin.indexer = indexer;

		connection.begin();
		connection.remove(geometry, GeoConstants.GEO_AS_GEO_JSON, original);
		connection.add(geometry, GeoConstants.GEO_AS_GEO_JSON, replacement);

		RepositoryException repositoryException = assertThrows(RepositoryException.class, connection::commit);
		PluginException pluginException = findCause(repositoryException, PluginException.class);
		assertSame(writeFailure, pluginException.getCause());
		assertEquals(1, indexer.indexedGeometryLists);
		assertEquals(1, indexer.rollbackCount);

		try (RepositoryConnection readConnection = repository.getConnection()) {
			assertTrue(readConnection.hasStatement(geometry, GeoConstants.GEO_AS_GEO_JSON, original, false));
			assertFalse(readConnection.hasStatement(geometry, GeoConstants.GEO_AS_GEO_JSON, replacement, false));
		}
	}

	@Test
	public void ignoreErrorsDoesNotHideClosedIndexFailure() {
		enablePlugin();
		executePluginControl(GeoSparqlPlugin.IGNORE_ERRORS_PREDICATE_IRI, VF.createLiteral(true));

		GeoSparqlPlugin plugin = geoSparqlPlugin();
		AlreadyClosedException closedIndex = new AlreadyClosedException("Simulated closed Lucene index");
		FailingIndexGeometryListIndexer indexer = new FailingIndexGeometryListIndexer(closedIndex);
		plugin.indexer = indexer;
		IRI geometry = VF.createIRI("http://example.com/closed-index-geometry");
		Literal sourceGeometryLiteral = VF.createLiteral("{\"type\":\"Point\",\"coordinates\":[1,2]}",
				GeoConstants.GEO_JSON_LITERAL);

		connection.begin();
		connection.add(geometry, GeoConstants.GEO_AS_GEO_JSON, sourceGeometryLiteral);
		RepositoryException repositoryException = assertThrows(RepositoryException.class, connection::commit);

		PluginException pluginException = findCause(repositoryException, PluginException.class);
		assertSame(closedIndex, pluginException.getCause());
		assertEquals(1, indexer.indexedGeometryLists);
		assertEquals(1, indexer.rollbackCount);
		try (RepositoryConnection readConnection = repository.getConnection()) {
			assertFalse(readConnection.hasStatement(geometry, GeoConstants.GEO_AS_GEO_JSON,
					sourceGeometryLiteral, false));
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

	private GeoSparqlPlugin geoSparqlPlugin() {
		OwlimSchemaRepository sail = (OwlimSchemaRepository) ((SailRepository) repository).getSail();
		return (GeoSparqlPlugin) sail.getPlugin("GeoSPARQL");
	}

	private static void assertPendingTransactionFailure(LuceneGeoIndexer indexer) {
		PluginException failure = assertThrows(PluginException.class,
				() -> indexer.getSourceGeometryLiteralsFor(0));
		assertTrue(failure.getMessage().contains("pending GraphDB transaction"));
	}

	private static final class FailingCompletionIndexer extends LuceneGeoIndexer {
		private boolean failCompletion;

		private FailingCompletionIndexer(GeoSparqlPlugin parent) {
			super(parent);
		}

		@Override
		protected void deleteObsoleteCommits() throws IOException {
			if (failCompletion) {
				throw new IOException("Simulated post-commit cleanup failure");
			}
			super.deleteObsoleteCommits();
		}
	}

	private static final class FailingPublishedCommitRollbackIndexer extends LuceneGeoIndexer {
		private final IOException commitFailure =
				new IOException("Simulated failure after publishing the Lucene commit");
		private boolean failCommitAfterPublish;
		private boolean failRestore;

		private FailingPublishedCommitRollbackIndexer(GeoSparqlPlugin parent) {
			super(parent);
		}

		@Override
		public void commit() throws Exception {
			super.commit();
			if (failCommitAfterPublish) {
				throw commitFailure;
			}
		}

		@Override
		protected void restorePreTransactionCommit() throws IOException {
			if (failRestore) {
				throw new IOException("Simulated rollback restoration failure");
			}
			super.restorePreTransactionCommit();
		}
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
		public CloseableIterator<CandidateEntity> getEnvelopeIntersections(IndexGeometry boundSourceIndexGeometry) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CloseableIterator<EnvelopeDisjointCandidate> getEnvelopeDisjointCandidates(
				IndexGeometry boundSourceIndexGeometry) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CloseableIterator<CandidateEntity> getNonSpatialCandidates() {
			throw new UnsupportedOperationException();
		}

		@Override
		public CloseableIterator<CandidateEntity> getAllEntities() {
			throw new UnsupportedOperationException();
		}

		@Override
		public CloseableIterator<SourceGeometryLiteral> getSourceGeometryLiteralsFor(long subject) {
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
		public void appendGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void freshIndex() {
			throw new UnsupportedOperationException();
		}
	}

	private static final class FailingIndexGeometryListIndexer implements GeoSparqlIndexer {
		private final Throwable writeFailure;
		private int indexedGeometryLists;
		private int rollbackCount;

		private FailingIndexGeometryListIndexer(Throwable writeFailure) {
			this.writeFailure = writeFailure;
		}

		@Override
		public void initialize() {
		}

		@Override
		public void indexGeometryList(long subject, Function<Long, String> subjectMapper,
				List<IndexGeometry> geometries) {
			indexedGeometryLists++;
			throw new PluginException("Unable to persist GeoSPARQL Lucene documents for subject "
					+ subjectMapper.apply(subject), writeFailure);
		}

		@Override
		public CloseableIterator<CandidateEntity> getEnvelopeIntersections(IndexGeometry boundSourceIndexGeometry) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CloseableIterator<EnvelopeDisjointCandidate> getEnvelopeDisjointCandidates(
				IndexGeometry boundSourceIndexGeometry) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CloseableIterator<CandidateEntity> getNonSpatialCandidates() {
			throw new UnsupportedOperationException();
		}

		@Override
		public CloseableIterator<CandidateEntity> getAllEntities() {
			throw new UnsupportedOperationException();
		}

		@Override
		public CloseableIterator<SourceGeometryLiteral> getSourceGeometryLiteralsFor(long subject) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void initSettings() {
		}

		@Override
		public void begin() {
		}

		@Override
		public void commit() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void rollback() {
			rollbackCount++;
		}

		@Override
		public void appendGeometry(long subject, Function<Long, String> subjectMapper, IndexGeometry geometry) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void freshIndex() {
			throw new UnsupportedOperationException();
		}
	}
}
