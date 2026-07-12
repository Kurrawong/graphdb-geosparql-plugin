package com.ontotext.trree.geosparql.lucene;

import com.ontotext.trree.geosparql.EntityGeometryIterator;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.locationtech.jts.geom.Geometry;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.*;

import java.io.IOException;

/**
 * An EntityGeometryIterator implementation that returns all Geometries matching a Lucene query.
 */
class LuceneEntityGeometryIterator implements EntityGeometryIterator {
	private final static int PAGE_SIZE = 1000;

	private TopDocs topDocs;
	private final IndexSearcher searcher;
	private final Query query;
	private final SourceGeometryLiteralResolver sourceResolver = new SourceGeometryLiteralResolver();
	private boolean exhausted;
	private boolean hasEntityId;

	private int idx = -1;

	private long entityId;
	private Geometry geometry;
	private SourceGeometryLiteral sourceGeometryLiteral;
	private IndexGeometry indexGeometry;

	LuceneEntityGeometryIterator(IndexSearcher searcher, Query query) throws IOException {
		this.searcher = searcher;
		this.query = query;

		topDocs = searcher.search(query, PAGE_SIZE);
	}

	@Override
	public long getEntityForLastGeometry() {
		return entityId;
	}

	@Override
	public Geometry nextGeometry() {
		if (!hasNextGeometry()) {
			return null;
		}

		try {
			advanceLuceneDocument();
			return geometry;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Geometry lastGeometry() {
		return geometry;
	}

	@Override
	public SourceGeometryLiteral lastSourceGeometryLiteral() {
		return sourceGeometryLiteral;
	}

	@Override
	public IndexGeometry lastIndexGeometry() {
		return indexGeometry;
	}

	@Override
	public boolean hasNextGeometry() {
		if (idx + 1 < topDocs.scoreDocs.length) {
			return true;
		}
		return loadNextPage();
	}

	@Override
	public void advanceToNextEntity() {
	}

	@Override
	public void close() throws IOException {
		sourceResolver.clear();
		searcher.getIndexReader().close();
	}

	private boolean loadNextPage() {
		if (exhausted) {
			return false;
		}
		if (topDocs.scoreDocs.length == 0 || topDocs.scoreDocs.length < PAGE_SIZE) {
			exhausted = true;
			return false;
		}
		try {
			ScoreDoc last = topDocs.scoreDocs[topDocs.scoreDocs.length - 1];
			topDocs = searcher.searchAfter(last, query, PAGE_SIZE);
			idx = -1;
			if (topDocs.scoreDocs.length == 0) {
				exhausted = true;
				return false;
			}
			return true;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void advanceLuceneDocument() throws IOException {
		idx++;
		ScoreDoc d = topDocs.scoreDocs[idx];
		Document doc = searcher.doc(d.doc);

		long documentEntityId = LuceneGeoDocumentSchema.entityId(doc);
		if (!hasEntityId || documentEntityId != entityId) {
			sourceResolver.clear();
		}
		entityId = documentEntityId;
		hasEntityId = true;
		indexGeometry = LuceneGeoDocumentSchema.indexGeometry(doc, sourceResolver);
		geometry = indexGeometry.indexGeometry();
		sourceGeometryLiteral = indexGeometry.sourceGeometryLiteral();
	}
}
