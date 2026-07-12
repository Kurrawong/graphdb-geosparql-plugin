package com.ontotext.trree.geosparql.lucene;

import com.ontotext.trree.geosparql.EntityGeometryIterator;
import com.ontotext.trree.geosparql.EntityIdIterator;
import com.ontotext.trree.geosparql.SingleEntityGeometryIterator;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.sdk.PluginException;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Page-backed iterator over unique entity ids matching a Lucene query.
 */
class LuceneEntityIdIterator implements EntityIdIterator {
	private static final int PAGE_SIZE = 1000;
	private static final Sort ENTITY_ID_SORT = new Sort(new SortField(LuceneGeoDocumentSchema.FIELD_ID,
			SortField.Type.LONG));

	private final IndexSearcher searcher;
	private final Query query;
	private final SourceGeometryLiteralResolver sourceResolver = new SourceGeometryLiteralResolver();

	private TopDocs topDocs;
	private int idx;
	private boolean exhausted;
	private boolean nextReady;
	private long nextEntityId;
	private List<IndexGeometry> nextGeometries;
	private long lastEntityId;
	private List<IndexGeometry> lastGeometries;
	private boolean hasLastEntityId;
	private Document bufferedDocument;

	LuceneEntityIdIterator(IndexSearcher searcher, Query query) {
		this.searcher = searcher;
		this.query = query;
	}

	@Override
	public boolean hasNextEntityId() {
		if (nextReady) {
			return true;
		}
		return loadNextUniqueEntityId();
	}

	@Override
	public long nextEntityId() {
		if (!hasNextEntityId()) {
			return 0;
		}
		nextReady = false;
		lastEntityId = nextEntityId;
		lastGeometries = nextGeometries;
		nextGeometries = null;
		hasLastEntityId = true;
		return lastEntityId;
	}

	@Override
	public EntityGeometryIterator getGeometriesForLastEntity() {
		if (!hasLastEntityId) {
			throw new PluginException("No Lucene candidate entity id has been returned yet.");
		}
		return new SingleEntityGeometryIterator(lastEntityId, lastGeometries);
	}

	@Override
	public void close() throws IOException {
		sourceResolver.clear();
		searcher.getIndexReader().close();
	}

	private boolean loadNextUniqueEntityId() {
		try {
			Document firstDocument = nextDocument();
			if (firstDocument == null) {
				return false;
			}
			long entityId = LuceneGeoDocumentSchema.entityId(firstDocument);
			sourceResolver.clear();
			List<IndexGeometry> geometries = new ArrayList<>();
			geometries.add(LuceneGeoDocumentSchema.indexGeometry(firstDocument, sourceResolver));

			while (true) {
				Document document = nextDocument();
				if (document == null) {
					return setNextEntity(entityId, geometries);
				}
				long documentEntityId = LuceneGeoDocumentSchema.entityId(document);
				if (documentEntityId != entityId) {
					bufferedDocument = document;
					return setNextEntity(entityId, geometries);
				}
				geometries.add(LuceneGeoDocumentSchema.indexGeometry(document, sourceResolver));
			}
		} catch (IOException e) {
			throw new PluginException("Unable to read Lucene entity id documents.", e);
		}
	}

	private void loadFirstPage() {
		try {
			topDocs = searcher.search(query, PAGE_SIZE, ENTITY_ID_SORT);
			idx = 0;
		} catch (IOException e) {
			throw new PluginException("Unable to execute Lucene entity id query.", e);
		}
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
			topDocs = searcher.searchAfter(last, query, PAGE_SIZE, ENTITY_ID_SORT);
			idx = 0;
			if (topDocs.scoreDocs.length == 0) {
				exhausted = true;
				return false;
			}
			return true;
		} catch (IOException e) {
			throw new PluginException("Unable to page Lucene entity id query.", e);
		}
	}

	private Document nextDocument() throws IOException {
		if (bufferedDocument != null) {
			Document document = bufferedDocument;
			bufferedDocument = null;
			return document;
		}
		if (topDocs == null) {
			loadFirstPage();
		}
		while (idx >= topDocs.scoreDocs.length) {
			if (!loadNextPage()) {
				return null;
			}
		}
		ScoreDoc scoreDoc = topDocs.scoreDocs[idx++];
		return searcher.doc(scoreDoc.doc);
	}

	private boolean setNextEntity(long entityId, List<IndexGeometry> geometries) {
		nextEntityId = entityId;
		nextGeometries = geometries;
		nextReady = true;
		return true;
	}
}
