package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Traverses candidate entities for one bound side of a GeoSPARQL property relation.
 *
 * <p>A full-scan policy opens one entity traversal and leaves exact evaluation to compare each candidate's complete
 * source set with the complete bound source set. An envelope policy opens one Lucene traversal per supplied non-empty
 * bound source and retains that source alongside every candidate so exact evaluation checks the pair admitted by
 * that envelope lookup.
 *
 * <p>The traversal owns its active candidate iterator. It closes one envelope lookup before opening the next and
 * closes the active iterator when this traversal is closed early.
 */
final class RelationCandidateTraversal implements CloseableIterator<RelationCandidateTraversal.Candidate> {
	private final GeoSparqlIndexer indexer;
	private final CandidateLookupPolicy candidateLookupPolicy;
	private final Iterator<IndexGeometry> boundIndexGeometries;
	private final Logger logger;

	private CloseableIterator<CandidateEntity> currentCandidates;
	private SourceGeometryLiteral currentBoundSource;
	private Candidate next;
	private boolean fullScanOpened;
	private boolean closed;

	RelationCandidateTraversal(GeoSparqlIndexer indexer, CandidateLookupPolicy candidateLookupPolicy,
			Collection<IndexGeometry> boundIndexGeometries, Logger logger) {
		this.indexer = indexer;
		this.candidateLookupPolicy = candidateLookupPolicy;
		this.boundIndexGeometries = boundIndexGeometries.iterator();
		this.logger = logger;
	}

	@Override
	public boolean hasNext() {
		if (closed) {
			return false;
		}
		if (next != null) {
			return true;
		}
		next = loadNextCandidate();
		return next != null;
	}

	@Override
	public Candidate next() {
		if (!hasNext()) {
			throw new NoSuchElementException("No more relation candidates.");
		}
		Candidate result = next;
		next = null;
		return result;
	}

	@Override
	public void close() {
		closed = true;
		closeCurrentCandidates();
		next = null;
	}

	private Candidate loadNextCandidate() {
		if (candidateLookupPolicy == CandidateLookupPolicy.FULL_SCAN) {
			return loadNextFullScanCandidate();
		}
		return loadNextEnvelopeCandidate();
	}

	private Candidate loadNextFullScanCandidate() {
		if (!fullScanOpened) {
			currentCandidates = indexer.getAllEntities();
			fullScanOpened = true;
		}
		if (currentCandidates.hasNext()) {
			return Candidate.forFullScan(currentCandidates.next());
		}
		return null;
	}

	private Candidate loadNextEnvelopeCandidate() {
		while (true) {
			if (currentCandidates != null) {
				if (currentCandidates.hasNext()) {
					return Candidate.forBoundSource(currentCandidates.next(), currentBoundSource);
				}
				closeCurrentCandidates();
			}

			if (!boundIndexGeometries.hasNext()) {
				return null;
			}
			IndexGeometry boundIndexGeometry = boundIndexGeometries.next();
			if (!boundIndexGeometry.isSpatialCandidate()) {
				continue;
			}
			currentBoundSource = boundIndexGeometry.sourceGeometryLiteral();
			currentCandidates = indexer.getEnvelopeIntersections(boundIndexGeometry);
		}
	}

	private void closeCurrentCandidates() {
		if (currentCandidates == null) {
			return;
		}
		try {
			currentCandidates.close();
		} catch (IOException e) {
			logger.warn("Unable to close GeoSPARQL iterator.", e);
		} finally {
			currentCandidates = null;
			currentBoundSource = null;
		}
	}

	/** Candidate entity plus the bound source responsible for an envelope match, if applicable. */
	static final class Candidate {
		private final CandidateEntity candidateEntity;
		private final Optional<SourceGeometryLiteral> boundSourceGeometryLiteral;

		private Candidate(CandidateEntity candidateEntity,
				Optional<SourceGeometryLiteral> boundSourceGeometryLiteral) {
			this.candidateEntity = candidateEntity;
			this.boundSourceGeometryLiteral = boundSourceGeometryLiteral;
		}

		private static Candidate forFullScan(CandidateEntity candidateEntity) {
			return new Candidate(candidateEntity, Optional.empty());
		}

		private static Candidate forBoundSource(CandidateEntity candidateEntity,
				SourceGeometryLiteral boundSourceGeometryLiteral) {
			return new Candidate(candidateEntity, Optional.of(boundSourceGeometryLiteral));
		}

		CandidateEntity candidateEntity() {
			return candidateEntity;
		}

		Optional<SourceGeometryLiteral> boundSourceGeometryLiteral() {
			return boundSourceGeometryLiteral;
		}
	}
}
