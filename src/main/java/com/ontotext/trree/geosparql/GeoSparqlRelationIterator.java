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
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Evaluates GeoSPARQL property relations by using Lucene for candidate lookup
 * and Jena for exact predicate evaluation.
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
	private MultiGeometryCandidateEntityIterator candidateEntityIterator;
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
		if (boundSubject != 0 && boundObject != 0) {
			return nextBoundPair();
		}
		if (boundSubject == 0 && boundObject == 0) {
			return false;
		}

		MultiGeometryCandidateEntityIterator candidates = candidateEntityIterator();
		while (candidates.hasNextEntityId()) {
			long candidateEntityId = candidates.nextEntityId();
			EntityPair entityPair = currentEntityPair(candidateEntityId);
			if (emittedEntityPairs.contains(entityPair)) {
				continue;
			}
			if (boundSubject == 0) {
				EntityGeometries candidateSubjectGeometries = geometriesForCandidateEntity(candidates,
						candidateEntityId);
				if (relationHolds(candidateSubjectGeometries, candidates.boundSourceGeometryLiteralForLastLookup())
						&& emittedEntityPairs.add(entityPair)) {
					subject = candidateEntityId;
					object = boundObject;
					logMatch();
					return true;
				}
			} else {
				EntityGeometries candidateObjectGeometries = geometriesForCandidateEntity(candidates,
						candidateEntityId);
				if (relationHolds(candidates.boundSourceGeometryLiteralForLastLookup(), candidateObjectGeometries)
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
		closeEntityIdIterator(candidateEntityIterator);
	}

	private boolean nextBoundPair() {
		if (boundPairEvaluated) {
			return false;
		}
		boundPairEvaluated = true;
		if (!relationHolds(boundSubjectGeometries(), boundObjectGeometries())) {
			return false;
		}
		subject = boundSubject;
		object = boundObject;
		logMatch();
		return true;
	}

	private MultiGeometryCandidateEntityIterator candidateEntityIterator() {
		if (candidateEntityIterator == null) {
			if (boundSubject == 0) {
				candidateEntityIterator = new MultiGeometryCandidateEntityIterator(boundObjectGeometries(),
						relation.getCandidateLookupPolicy());
			} else {
				candidateEntityIterator = new MultiGeometryCandidateEntityIterator(boundSubjectGeometries(),
						relation.getInverseCandidateLookupPolicy());
			}
		}
		return candidateEntityIterator;
	}

	private EntityPair currentEntityPair(long candidateEntityId) {
		if (boundSubject == 0) {
			return new EntityPair(candidateEntityId, boundObject);
		}
		return new EntityPair(boundSubject, candidateEntityId);
	}

	private boolean relationHolds(EntityGeometries subjectGeometries, EntityGeometries objectGeometries) {
		if (subjectGeometries.isEmpty() || objectGeometries.isEmpty()) {
			return false;
		}
		for (SourceGeometryLiteral subjectGeometry : subjectGeometries.sourceGeometryLiterals) {
			for (SourceGeometryLiteral objectGeometry : objectGeometries.sourceGeometryLiterals) {
				if (relation.evaluate(subjectGeometry, objectGeometry)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean relationHolds(EntityGeometries subjectGeometries, SourceGeometryLiteral objectGeometry) {
		if (subjectGeometries.isEmpty()) {
			return false;
		}
		for (SourceGeometryLiteral subjectGeometry : subjectGeometries.sourceGeometryLiterals) {
			if (relation.evaluate(subjectGeometry, objectGeometry)) {
				return true;
			}
		}
		return false;
	}

	private boolean relationHolds(SourceGeometryLiteral subjectGeometry, EntityGeometries objectGeometries) {
		if (objectGeometries.isEmpty()) {
			return false;
		}
		for (SourceGeometryLiteral objectGeometry : objectGeometries.sourceGeometryLiterals) {
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
			IndexGeometry indexGeometry = parent.getIndexGeometryFromLiteral((Literal) value, datatype);
			return EntityGeometries.singleton(indexGeometry);
		}

		EntityGeometryIterator iterator = parent.indexer.getGeometriesFor(entityId);
		if (iterator == null) {
			throw new PluginException("Unable to create GeoSPARQL geometry iterator for entity id " + entityId);
		}

		return geometriesFromIterator(iterator);
	}

	private EntityGeometries geometriesForCandidateEntity(EntityIdIterator candidates, long entityId) {
		EntityGeometryIterator iterator = candidates.getGeometriesForLastEntity();
		if (iterator == null) {
			return geometriesForEntity(entityId);
		}
		return geometriesFromIterator(iterator);
	}

	private EntityGeometries geometriesFromIterator(EntityGeometryIterator iterator) {
		EntityGeometries geometries = new EntityGeometries();
		try {
			while (iterator.hasNextGeometry()) {
				Geometry indexGeometry = iterator.nextGeometry();
				SourceGeometryLiteral sourceGeometryLiteral = iterator.lastSourceGeometryLiteral();
				geometries.add(indexGeometry, sourceGeometryLiteral);
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

	private void closeIterator(EntityGeometryIterator iterator) {
		try {
			iterator.close();
		} catch (IOException e) {
			logger.warn("Unable to close entity-geometry iterator.", e);
		}
	}

	private void closeEntityIdIterator(EntityIdIterator iterator) {
		if (iterator == null) {
			return;
		}
		try {
			iterator.close();
		} catch (IOException e) {
			logger.warn("Unable to close entity-id iterator.", e);
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
		private final List<Geometry> indexGeometries = new ArrayList<>();
		private final List<SourceGeometryLiteral> sourceGeometryLiterals = new ArrayList<>();

		private static EntityGeometries singleton(IndexGeometry indexGeometry) {
			EntityGeometries geometries = new EntityGeometries();
			geometries.add(indexGeometry.indexGeometry(), indexGeometry.sourceGeometryLiteral());
			return geometries;
		}

		private void add(Geometry indexGeometry, SourceGeometryLiteral sourceGeometryLiteral) {
			if (indexGeometry == null || sourceGeometryLiteral == null) {
				throw new PluginException("GeoSPARQL entity geometry is missing index or source geometry literal.");
			}
			indexGeometries.add(indexGeometry);
			sourceGeometryLiterals.add(sourceGeometryLiteral);
		}

		private boolean isEmpty() {
			return sourceGeometryLiterals.isEmpty();
		}
	}

	private final class MultiGeometryCandidateEntityIterator implements EntityIdIterator {
		private final EntityGeometries geometries;
		private final CandidateLookupPolicy candidateLookupPolicy;
		private EntityIdIterator currentIterator;
		private EntityIdIterator nextIterator;
		private EntityIdIterator lastIterator;
		private SourceGeometryLiteral currentBoundSourceGeometryLiteral;
		private SourceGeometryLiteral nextBoundSourceGeometryLiteral;
		private SourceGeometryLiteral lastBoundSourceGeometryLiteral;
		private int geometryIndex;
		private boolean nextReady;
		private long nextEntityId;

		private MultiGeometryCandidateEntityIterator(EntityGeometries geometries,
													 CandidateLookupPolicy candidateLookupPolicy) {
			this.geometries = geometries;
			this.candidateLookupPolicy = candidateLookupPolicy;
		}

		@Override
		public boolean hasNextEntityId() {
			if (nextReady) {
				return true;
			}
			return loadNextEntityId();
		}

		@Override
		public long nextEntityId() {
			if (!hasNextEntityId()) {
				return 0;
			}
			lastIterator = nextIterator;
			nextIterator = null;
			lastBoundSourceGeometryLiteral = nextBoundSourceGeometryLiteral;
			nextBoundSourceGeometryLiteral = null;
			nextReady = false;
			return nextEntityId;
		}

		@Override
		public EntityGeometryIterator getGeometriesForLastEntity() {
			if (lastIterator == null) {
				throw new PluginException("No GeoSPARQL candidate entity id has been returned yet.");
			}
			return lastIterator.getGeometriesForLastEntity();
		}

		private SourceGeometryLiteral boundSourceGeometryLiteralForLastLookup() {
			if (lastBoundSourceGeometryLiteral == null) {
				throw new PluginException("No GeoSPARQL candidate entity id has been returned yet.");
			}
			return lastBoundSourceGeometryLiteral;
		}

		@Override
		public void close() {
			closeEntityIdIterator(currentIterator);
			currentIterator = null;
		}

		private boolean loadNextEntityId() {
			while (true) {
				if (currentIterator != null) {
					while (currentIterator.hasNextEntityId()) {
						long entityId = currentIterator.nextEntityId();
						nextEntityId = entityId;
						nextIterator = currentIterator;
						nextBoundSourceGeometryLiteral = currentBoundSourceGeometryLiteral;
						nextReady = true;
						return true;
					}
					closeEntityIdIterator(currentIterator);
					currentIterator = null;
				}

				if (geometryIndex >= geometries.indexGeometries.size()) {
					return false;
				}
				currentBoundSourceGeometryLiteral = geometries.sourceGeometryLiterals.get(geometryIndex);
				currentIterator = parent.indexer.getMatchingEntityIds(geometries.indexGeometries.get(geometryIndex++),
						candidateLookupPolicy);
			}
		}
	}
}
