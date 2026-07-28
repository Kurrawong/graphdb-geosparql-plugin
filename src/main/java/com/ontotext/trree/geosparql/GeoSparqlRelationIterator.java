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
import java.util.Set;

/**
 * Produces statement matches for a GeoSPARQL property relation.
 *
 * <p>With one side bound, Lucene supplies grouped candidate entities and Jena evaluates the relation against their
 * source geometry literal snapshots. Full-scan policies evaluate the complete source sets; spatial lookups evaluate
 * only sources represented by matching Lucene documents. A fully bound pair bypasses candidate lookup.
 *
 * <p>Multiple source-document hits and repeated bound-geometry lookups may encounter the same pair, so accepted entity
 * pairs are emitted once. Closing the statement iterator also closes the active Lucene reader.
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
	private final Set<EntityPair> emittedEntityPairs = new HashSet<>();

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
			CandidateEntity candidate = candidateLookup.candidateEntity();
			long candidateEntityId = candidate.entityId();
			EntityPair entityPair = currentEntityPair(candidateEntityId);
			if (emittedEntityPairs.contains(entityPair)) {
				continue;
			}
			if (boundSubject == 0) {
				List<SourceGeometryLiteral> candidateSubjectGeometries =
						candidate.matchingSourceGeometryLiterals();
				boolean holds = candidateLookup.boundSourceGeometryLiteral().isEmpty()
						? relationHolds(candidateSubjectGeometries,
								boundObjectGeometries().sourceGeometryLiterals())
						: relationHolds(candidateSubjectGeometries,
								candidateLookup.boundSourceGeometryLiteral().get());
				if (holds
						&& emittedEntityPairs.add(entityPair)) {
					subject = candidateEntityId;
					object = boundObject;
					logMatch();
					return true;
				}
			} else {
				List<SourceGeometryLiteral> candidateObjectGeometries =
						candidate.matchingSourceGeometryLiterals();
				boolean holds = candidateLookup.boundSourceGeometryLiteral().isEmpty()
						? relationHolds(boundSubjectGeometries().sourceGeometryLiterals(),
								candidateObjectGeometries)
						: relationHolds(candidateLookup.boundSourceGeometryLiteral().get(),
								candidateObjectGeometries);
				if (holds
						&& emittedEntityPairs.add(entityPair)) {
					subject = boundSubject;
					object = candidateEntityId;
					logMatch();
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
			candidateIterator = new RelationCandidateTraversal(parent.indexer, relation.getCandidateLookupPolicy(),
					boundGeometries.indexGeometries(), logger);
		}
		return candidateIterator;
	}

	private EntityPair currentEntityPair(long candidateEntityId) {
		if (boundSubject == 0) {
			return new EntityPair(candidateEntityId, boundObject);
		}
		return new EntityPair(boundSubject, candidateEntityId);
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

	private static final class EntityPair {
		private final long subject;
		private final long object;

		private EntityPair(long subject, long object) {
			this.subject = subject;
			this.object = object;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof EntityPair)) {
				return false;
			}
			EntityPair that = (EntityPair) other;
			return subject == that.subject && object == that.object;
		}

		@Override
		public int hashCode() {
			int result = Long.hashCode(subject);
			result = 31 * result + Long.hashCode(object);
			return result;
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
