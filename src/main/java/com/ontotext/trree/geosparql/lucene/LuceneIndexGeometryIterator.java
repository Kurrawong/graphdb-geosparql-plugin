package com.ontotext.trree.geosparql.lucene;

import com.ontotext.trree.geosparql.CloseableIterator;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.sdk.PluginException;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Streams decoded {@link IndexGeometry} values from a page-backed Lucene query.
 *
 * <p>Consecutive documents for the same entity share a validated source geometry literal snapshot. That reuse state
 * is cleared when the entity id changes and when the iterator closes. This iterator owns the searcher's reader and
 * releases it on {@link #close()}.
 */
class LuceneIndexGeometryIterator implements CloseableIterator<IndexGeometry> {
	private static final int PAGE_SIZE = 1000;

	private final IndexSearcher searcher;
	private final Query query;
	private final SourceGeometryLiteralResolver sourceResolver = new SourceGeometryLiteralResolver();
	private TopDocs topDocs;
	private int idx = -1;
	private boolean exhausted;
	private boolean hasEntityId;
	private long entityId;
	private boolean closed;

	LuceneIndexGeometryIterator(IndexSearcher searcher, Query query) throws IOException {
		this.searcher = searcher;
		this.query = query;
		topDocs = searcher.search(query, PAGE_SIZE);
	}

	@Override
	public boolean hasNext() {
		if (idx + 1 < topDocs.scoreDocs.length) {
			return true;
		}
		return loadNextPage();
	}

	@Override
	public IndexGeometry next() {
		if (!hasNext()) {
			throw new NoSuchElementException("No more Lucene index geometries.");
		}
		try {
			ScoreDoc scoreDoc = topDocs.scoreDocs[++idx];
			Document document = searcher.doc(scoreDoc.doc);
			long documentEntityId = LuceneGeoDocumentSchema.entityId(document);
			if (!hasEntityId || documentEntityId != entityId) {
				sourceResolver.clear();
			}
			entityId = documentEntityId;
			hasEntityId = true;
			return LuceneGeoDocumentSchema.indexGeometry(document, sourceResolver);
		} catch (IOException e) {
			throw new PluginException("Unable to read Lucene index geometry document.", e);
		}
	}

	@Override
	public void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
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
			throw new PluginException("Unable to page Lucene index geometry query.", e);
		}
	}
}
