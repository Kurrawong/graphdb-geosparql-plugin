package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.lucene.LuceneMultiSearchEntityGeometryIterator;
import com.ontotext.trree.sdk.Entities;
import com.ontotext.trree.sdk.PluginException;
import com.ontotext.trree.sdk.StatementIterator;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.locationtech.jts.geom.Geometry;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * Evaluates GeoSPARQL property relations by using Lucene for candidate lookup
 * and Jena for exact predicate evaluation.
 */
class GeoSparqlRelationIterator extends StatementIterator {
	private final GeoSparqlPlugin parent;
	private final Logger logger;
	private final GeoSparqlPropertyRelation relation;
	private final Entities entities;

	private Geometry knownGeometry;
	private SourceGeometryLiteral knownSourceGeometryLiteral;
	private EntityGeometryIterator iKnownEntities;
	private EntityGeometryIterator iCandidateEntities;

	private EntityGeometryIterator iSubjectGeometries;
	private EntityGeometryIterator iObjectGeometries;

	private LuceneMultiSearchEntityGeometryIterator searchIterator;

	private boolean inverse;

	GeoSparqlRelationIterator(GeoSparqlPlugin parent, GeoSparqlPropertyRelation relation,
							   long subject, long predicate, long object, Entities entities) {
		this.parent = parent;
		this.logger = parent.getLogger();
		this.relation = relation;
		this.subject = subject;
		this.predicate = predicate;
		this.object = object;
		this.entities = entities;

		if (subject != 0) {
			// Subject is bound and refers to a Geometry literal or a Geometry/Feature object
			iSubjectGeometries = makeIteratorFromEntityId(subject);
		}

		if (object != 0) {
			// Object is bound and refers to a Geometry literal or a Geometry/Feature object
			iObjectGeometries = makeIteratorFromEntityId(object);
		}

		if (iSubjectGeometries != null && iObjectGeometries != null) {
			// Both subject and object have explicit geometry candidates, so evaluate directly
			// without Lucene candidate lookup.
			iKnownEntities = iObjectGeometries;
			iCandidateEntities = iSubjectGeometries;

			inverse = true;
		} else if (iSubjectGeometries == null) {
			// Subject is unbound; Lucene candidate lookup will use the bound object index geometry.
			iKnownEntities = iObjectGeometries;
			iCandidateEntities = searchIterator = new LuceneMultiSearchEntityGeometryIterator(parent.indexer,
					relation.getCandidateLookupPolicy());

			inverse = true;
		} else {
			// Object is unbound; Lucene candidate lookup will use the bound subject index geometry.
			iKnownEntities = iSubjectGeometries;
			iCandidateEntities = searchIterator = new LuceneMultiSearchEntityGeometryIterator(parent.indexer,
					relation.getInverseCandidateLookupPolicy());

			inverse = false;
		}
	}

	@Override
	public boolean next() {
		boolean result = false;

		while (true) {
			if (knownGeometry == null || !iCandidateEntities.hasNextGeometry()) {
				if (!iKnownEntities.hasNextGeometry()) {
					// No more known entities; GeoSparqlRelationIterator ends.
					break;
				}

				// Fresh known index geometry. It is reused until a match is found or
				// no candidate geometries remain.
				knownGeometry = iKnownEntities.nextGeometry();
				knownSourceGeometryLiteral = iKnownEntities.lastSourceGeometryLiteral();

				if (searchIterator != null) {
					// If either subject or object is unbound, candidate lookup starts
					// from the new known geometry.
					searchIterator.search(knownGeometry);
				}

				if (logger.isDebugEnabled()) {
					logger.debug("KNOWN GEOMETRY: {}; {}", entities.get(iKnownEntities.getEntityForLastGeometry()),
							knownGeometry);
				}
			}

			Geometry candidateGeometry = iCandidateEntities.nextGeometry();
			SourceGeometryLiteral candidateSourceGeometryLiteral = iCandidateEntities.lastSourceGeometryLiteral();

			if (candidateGeometry != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("CANDIDATE: {}; {}", entities.get(iCandidateEntities.getEntityForLastGeometry()),
							candidateGeometry);
				}

				result = inverse
						? relation.evaluate(candidateSourceGeometryLiteral, knownSourceGeometryLiteral)
						: relation.evaluate(knownSourceGeometryLiteral, candidateSourceGeometryLiteral);
			} else {
				logger.debug(">>>>>>>> GeoSPARQL: No available candidate geometries matching the query!");
				break;
			}

			if (result) {
				// NB: Skips the remaining geometries for this lastEntity as we already found a match
				iKnownEntities.advanceToNextEntity();

				if (logger.isDebugEnabled()) {
					logger.debug("MATCH: {} -> {}", knownGeometry, candidateGeometry);
				}

				if (inverse) {
					subject = iCandidateEntities.getEntityForLastGeometry();
					object = iKnownEntities.getEntityForLastGeometry();
				} else {
					subject = iKnownEntities.getEntityForLastGeometry();
					object = iCandidateEntities.getEntityForLastGeometry();
				}

				// Found a subject/object pair that satisfies exact predicate evaluation.
				break;
			}
		}

		return result;
	}


	@Override
	public void close() {
		try {
			iKnownEntities.close();
		} catch (IOException e) {
			logger.warn("Unable to close entity-geometry iterator.", e);
		}

		try {
			iCandidateEntities.close();
		} catch (IOException e) {
			logger.warn("Unable to close entity-geometry iterator.", e);
		}
	}

	/**
	 * Makes an EntityGeometryIterator from a GraphDB entity id.
	 * The id may refer to either a Geometry literal (WKT/GML) or an IRI that describes
	 * a Geometry or Feature object.
	 *
	 * @param entityId an entity id
	 * @return an EntityGeometryIterator
	 */
	private EntityGeometryIterator makeIteratorFromEntityId(long entityId) {
		EntityGeometryIterator iterator;
		Value value = entities.get(entityId);
		if (value instanceof Literal) {
			IRI datatype = GeoConstants.GEO_GML_LITERAL.equals(((Literal) value).getDatatype())
					? GeoConstants.GEO_GML_LITERAL : GeoConstants.GEO_WKT_LITERAL;
			IndexGeometry indexGeometry = parent.getIndexGeometryFromLiteral((Literal) value, datatype);
			iterator = new SingleEntityGeometryIterator(entityId, indexGeometry);
		} else {
			// the id refers to an IRI referring to a geometry/feature
			iterator = parent.indexer.getGeometriesFor(entityId);
		}

		if (iterator == null) {
			throw new PluginException("Unable to create GeoSPARQL geometry iterator for entity id " + entityId);
		}

		return iterator;
	}
}
