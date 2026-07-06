package com.ontotext.trree.geosparql.lucene;

import com.ontotext.trree.geosparql.CandidateLookupPolicy;
import com.ontotext.trree.geosparql.EntityGeometryIterator;
import com.ontotext.trree.geosparql.GeoSparqlIndexer;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.locationtech.jts.geom.Geometry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Looks up each known index geometry in Lucene with the provided candidate lookup policy.
 */
public class LuceneMultiSearchEntityGeometryIterator implements EntityGeometryIterator {
	private GeoSparqlIndexer indexer;
	private CandidateLookupPolicy candidateLookupPolicy;
	private EntityGeometryIterator luceneIterator;
	private List<EntityGeometryIterator> iteratorsToClose = new ArrayList<>();

	public LuceneMultiSearchEntityGeometryIterator(GeoSparqlIndexer indexer,
												  CandidateLookupPolicy candidateLookupPolicy) {
		this.indexer = indexer;
		this.candidateLookupPolicy = candidateLookupPolicy;
	}

    @Override
    public long getEntityForLastGeometry() {
        return luceneIterator == null ? 0 : luceneIterator.getEntityForLastGeometry();
    }

    @Override
    public Geometry nextGeometry() {
        return luceneIterator == null ? null : luceneIterator.nextGeometry();
    }

    @Override
    public Geometry lastGeometry() {
        return luceneIterator == null ? null : luceneIterator.lastGeometry();
    }

    @Override
    public SourceGeometryLiteral lastSourceGeometryLiteral() {
        return luceneIterator == null ? null : luceneIterator.lastSourceGeometryLiteral();
    }

    @Override
    public boolean hasNextGeometry() {
        return luceneIterator != null && luceneIterator.hasNextGeometry();
    }

    @Override
    public void advanceToNextEntity() {
        if (luceneIterator == null) {
            return;
        }

        luceneIterator.advanceToNextEntity();
    }

    @Override
    public void close() throws IOException {
        for(EntityGeometryIterator itty : iteratorsToClose) {
            itty.close();
        }
    }

	public void search(Geometry geometry) {
		luceneIterator = indexer.getMatchingObjects(geometry, candidateLookupPolicy);
		iteratorsToClose.add(luceneIterator);
	}
}
