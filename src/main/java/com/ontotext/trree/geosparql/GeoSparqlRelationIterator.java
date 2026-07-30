package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import com.ontotext.trree.sdk.Entities;
import com.ontotext.trree.sdk.PluginException;
import com.ontotext.trree.sdk.StatementIterator;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Produces statement matches for a GeoSPARQL property relation.
 *
 * <p>With one side bound, Lucene supplies either envelope-proven entity ids or grouped uncertain candidates.
 * Envelope-proven disjoint matches need no source payload; uncertain candidates are evaluated against their source
 * geometry literal snapshots. The internal empty-bound fallback evaluates complete source sets, and a fully bound
 * pair bypasses candidate lookup.
 *
 * <p>One side is fixed for every candidate traversal, so the candidate entity id uniquely identifies the resulting
 * entity pair. Repeated source-document hits and bound-source lookups emit that candidate entity once. Closing the
 * statement iterator also closes the active Lucene reader.
 */
class GeoSparqlRelationIterator extends StatementIterator {
	private final GeoSparqlPlugin parent;
	private final Logger logger;
	private final GeoSparqlPropertyRelation relation;
	private final Entities entities;
	private final long boundSubject;
	private final long boundObject;
	private EntityGeometries boundSubjectGeometries;
	private EntityGeometries boundObjectGeometries;
	private RelationCandidateTraversal candidateIterator;
	private final Set<Long> emittedCandidateEntityIds = new HashSet<>();

	private boolean boundPairEvaluated;

	GeoSparqlRelationIterator(GeoSparqlPlugin parent, GeoSparqlPropertyRelation relation,
							   long subject, long predicate, long object, Entities entities) {
		this.parent = parent;
		this.logger = parent.getLogger();
		this.relation = relation;
		this.subject = subject;
		this.predicate = predicate;
		this.object = object;
		this.entities = entities;
		this.boundSubject = subject;
		this.boundObject = object;
	}

	@Override
	public boolean next() {
		try {
			return nextInternal();
		} catch (RuntimeException e) {
			close();
			throw e;
		}
	}

	private boolean nextInternal() {
		if (boundSubject != 0 && boundObject != 0) {
			return nextBoundPair();
		}
		if (boundSubject == 0 && boundObject == 0) {
			return false;
		}

		RelationCandidateTraversal candidates = candidateIterator();
		while (candidates.hasNext()) {
			RelationCandidateTraversal.Candidate candidateLookup = candidates.next();
			long candidateEntityId = candidateLookup.entityId();
			if (emittedCandidateEntityIds.contains(candidateEntityId)) {
				continue;
			}
			if (candidateLookup.matchCertainty()
					== RelationCandidateTraversal.MatchCertainty.DEFINITE_MATCH) {
				emittedCandidateEntityIds.add(candidateEntityId);
				setCurrentMatch(candidateEntityId);
				return true;
			}
			CandidateEntity candidate = candidateLookup.exactCandidateEntity();
			Optional<SourceGeometryLiteral> boundSourceGeometryLiteral =
					candidateLookup.boundSourceGeometryLiteral();
			if (boundSubject == 0) {
				List<SourceGeometryLiteral> candidateSubjectGeometries =
						candidate.matchingSourceGeometryLiterals();
				boolean holds = boundSourceGeometryLiteral.isEmpty()
						? relationHolds(candidateSubjectGeometries,
								boundObjectGeometries().sourceGeometryLiterals())
						: relationHolds(candidateSubjectGeometries,
								boundSourceGeometryLiteral.get());
				if (holds
						&& emittedCandidateEntityIds.add(candidateEntityId)) {
					setCurrentMatch(candidateEntityId);
					return true;
				}
			} else {
				List<SourceGeometryLiteral> candidateObjectGeometries =
						candidate.matchingSourceGeometryLiterals();
				boolean holds = boundSourceGeometryLiteral.isEmpty()
						? relationHolds(boundSubjectGeometries().sourceGeometryLiterals(),
								candidateObjectGeometries)
						: relationHolds(boundSourceGeometryLiteral.get(),
								candidateObjectGeometries);
				if (holds
						&& emittedCandidateEntityIds.add(candidateEntityId)) {
					setCurrentMatch(candidateEntityId);
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public void close() {
		closeIterator(candidateIterator);
	}

	private boolean nextBoundPair() {
		if (boundPairEvaluated) {
			return false;
		}
		boundPairEvaluated = true;
		if (!relationHolds(boundSubjectGeometries().sourceGeometryLiterals(),
				boundObjectGeometries().sourceGeometryLiterals())) {
			return false;
		}
		subject = boundSubject;
		object = boundObject;
		logMatch();
		return true;
	}

	private RelationCandidateTraversal candidateIterator() {
		if (candidateIterator == null) {
			EntityGeometries boundGeometries;
			if (boundSubject == 0) {
				boundGeometries = boundObjectGeometries();
			} else {
				boundGeometries = boundSubjectGeometries();
			}
			candidateIterator = new RelationCandidateTraversal(parent.indexer, relation,
					boundGeometries.indexGeometries(), logger);
		}
		return candidateIterator;
	}

	private void setCurrentMatch(long candidateEntityId) {
		if (boundSubject == 0) {
			subject = candidateEntityId;
			object = boundObject;
		} else {
			subject = boundSubject;
			object = candidateEntityId;
		}
		logMatch();
	}

	private boolean relationHolds(Collection<SourceGeometryLiteral> subjectGeometries,
			Collection<SourceGeometryLiteral> objectGeometries) {
		if (subjectGeometries.isEmpty() || objectGeometries.isEmpty()) {
			return false;
		}
		for (SourceGeometryLiteral subjectGeometry : subjectGeometries) {
			for (SourceGeometryLiteral objectGeometry : objectGeometries) {
				if (relation.evaluate(subjectGeometry, objectGeometry)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean relationHolds(Collection<SourceGeometryLiteral> subjectGeometries,
			SourceGeometryLiteral objectGeometry) {
		if (subjectGeometries.isEmpty()) {
			return false;
		}
		for (SourceGeometryLiteral subjectGeometry : subjectGeometries) {
			if (relation.evaluate(subjectGeometry, objectGeometry)) {
				return true;
			}
		}
		return false;
	}

	private boolean relationHolds(SourceGeometryLiteral subjectGeometry,
			Collection<SourceGeometryLiteral> objectGeometries) {
		if (objectGeometries.isEmpty()) {
			return false;
		}
		for (SourceGeometryLiteral objectGeometry : objectGeometries) {
			if (relation.evaluate(subjectGeometry, objectGeometry)) {
				return true;
			}
		}
		return false;
	}

	private EntityGeometries geometriesForEntity(long entityId) {
		Value value = entities.get(entityId);
		if (value instanceof Literal) {
			IRI datatype = GeoConstants.GEO_GML_LITERAL.equals(((Literal) value).getDatatype())
					? GeoConstants.GEO_GML_LITERAL : GeoConstants.GEO_WKT_LITERAL;
			return EntityGeometries.fromIndexGeometry(
					parent.getIndexGeometryFromLiteral((Literal) value, datatype));
		}

		CloseableIterator<SourceGeometryLiteral> iterator = parent.indexer.getSourceGeometryLiteralsFor(entityId);
		if (iterator == null) {
			throw new PluginException("Unable to create GeoSPARQL geometry iterator for entity id " + entityId);
		}

		return geometriesFromIterator(iterator);
	}

	private EntityGeometries geometriesFromIterator(CloseableIterator<SourceGeometryLiteral> iterator) {
		EntityGeometries geometries = new EntityGeometries();
		try {
			while (iterator.hasNext()) {
				geometries.addSource(iterator.next());
			}
		} finally {
			closeIterator(iterator);
		}
		return geometries;
	}

	private EntityGeometries boundSubjectGeometries() {
		if (boundSubjectGeometries == null) {
			boundSubjectGeometries = geometriesForEntity(boundSubject);
		}
		return boundSubjectGeometries;
	}

	private EntityGeometries boundObjectGeometries() {
		if (boundObjectGeometries == null) {
			boundObjectGeometries = geometriesForEntity(boundObject);
		}
		return boundObjectGeometries;
	}

	private void closeIterator(CloseableIterator<?> iterator) {
		if (iterator == null) {
			return;
		}
		try {
			iterator.close();
		} catch (IOException e) {
			logger.warn("Unable to close GeoSPARQL iterator.", e);
		}
	}

	private void logMatch() {
		if (logger.isDebugEnabled()) {
			logger.debug("GeoSPARQL relation match: {} -> {}", entities.get(subject), entities.get(object));
		}
	}

	private static final class EntityGeometries {
		private final Map<SourceGeometryLiteral, IndexGeometry> indexGeometriesBySource = new LinkedHashMap<>();

		private static EntityGeometries fromIndexGeometry(IndexGeometry indexGeometry) {
			EntityGeometries geometries = new EntityGeometries();
			geometries.add(indexGeometry);
			return geometries;
		}

		private void add(IndexGeometry indexGeometry) {
			if (indexGeometry == null || indexGeometry.sourceGeometryLiteral() == null) {
				throw new PluginException("GeoSPARQL entity geometry is missing index or source geometry literal.");
			}
			indexGeometriesBySource.putIfAbsent(indexGeometry.sourceGeometryLiteral(), indexGeometry);
		}

		private void addSource(SourceGeometryLiteral sourceGeometryLiteral) {
			add(IndexGeometry.fromSourceGeometryLiteral(sourceGeometryLiteral));
		}

		private Set<SourceGeometryLiteral> sourceGeometryLiterals() {
			return indexGeometriesBySource.keySet();
		}

		private Collection<IndexGeometry> indexGeometries() {
			return indexGeometriesBySource.values();
		}
	}
}
