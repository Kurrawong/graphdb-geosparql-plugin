package com.ontotext.trree.geosparql.lucene;

import com.ontotext.trree.geosparql.CloseableIterator;
import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.JenaGeoSparqlException;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.sdk.PluginException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial.ShapeValues;
import org.apache.lucene.spatial.ShapeValuesSource;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.exception.InvalidShapeException;

import java.io.EOFException;
import java.io.IOException;
import java.util.List;
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
	private final ShapeValuesSource indexGeometryValuesSource;
	private final JtsSpatialContext spatialContext;
	private final List<LeafReaderContext> leaves;
	private final SourceGeometryLiteralResolver sourceResolver = new SourceGeometryLiteralResolver();
	private TopDocs topDocs;
	private int idx = -1;
	private boolean exhausted;
	private boolean hasEntityId;
	private long entityId;
	private boolean closed;
	private int currentLeafIndex = -1;
	private ShapeValues currentIndexGeometryValues;

	LuceneIndexGeometryIterator(IndexSearcher searcher, Query query,
			SerializedDVStrategy geometryStrategy, JtsSpatialContext spatialContext) throws IOException {
		this.searcher = searcher;
		this.query = query;
		this.indexGeometryValuesSource = geometryStrategy.makeShapeValueSource();
		this.spatialContext = spatialContext;
		this.leaves = searcher.getIndexReader().leaves();
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
			SourceGeometryLiteral source = LuceneGeoDocumentSchema.sourceGeometryLiteral(document, sourceResolver);
			Geometry indexGeometry = readIndexGeometry(scoreDoc.doc, document, source);
			return LuceneGeoDocumentSchema.indexGeometry(document, source, indexGeometry);
		} catch (IOException | RuntimeException e) {
			throw classifyReadFailure(e);
		}
	}

	static RuntimeException classifyReadFailure(Exception failure) {
		if (failure instanceof PluginException) {
			return (PluginException) failure;
		}
		if (failure instanceof JenaGeoSparqlException) {
			if (((JenaGeoSparqlException) failure).isUnsupportedCrs()) {
				// Reindexing cannot repair missing CRS deployment data. Preserve the actionable SIS guidance so
				// bound-entity lookup reports the same failure as candidate and exact-evaluation paths.
				return (JenaGeoSparqlException) failure;
			}
			return new PluginException(LuceneGeoDocumentSchema.SCHEMA_MISMATCH_MESSAGE, failure);
		}
		if (failure instanceof CorruptIndexException || failure instanceof EOFException) {
			return new PluginException(LuceneGeoDocumentSchema.SCHEMA_MISMATCH_MESSAGE, failure);
		}
		if (failure instanceof IOException) {
			return new PluginException("Unable to read Lucene index geometry document.", failure);
		}
		return (RuntimeException) failure;
	}

	private Geometry readIndexGeometry(int globalDocumentId, Document document,
			SourceGeometryLiteral source) throws IOException {
		int leafIndex = ReaderUtil.subIndex(globalDocumentId, leaves);
		LeafReaderContext leaf = leaves.get(leafIndex);
		if (leafIndex != currentLeafIndex) {
			currentLeafIndex = leafIndex;
			currentIndexGeometryValues = indexGeometryValuesSource.getValues(leaf);
		}

		boolean hasIndexGeometry = currentIndexGeometryValues.advanceExact(globalDocumentId - leaf.docBase);
		boolean sentinel = IndexGeometry.BUILD_MODE_EMPTY_SENTINEL.equals(
				document.get(LuceneGeoDocumentSchema.FIELD_INDEX_BUILD_MODE));
		if (sentinel) {
			if (hasIndexGeometry) {
				throw new PluginException(LuceneGeoDocumentSchema.SCHEMA_MISMATCH_MESSAGE);
			}
			return source.asGeometryWrapper().getXYGeometry().copy();
		}
		if (!hasIndexGeometry) {
			throw new PluginException(LuceneGeoDocumentSchema.SCHEMA_MISMATCH_MESSAGE);
		}
		try {
			return spatialContext.getShapeFactory().getGeometryFrom(currentIndexGeometryValues.value()).copy();
		} catch (InvalidShapeException | IllegalArgumentException e) {
			// These failures occur after Lucene has supplied the stored binary value and indicate a malformed
			// Spatial4j payload, rather than an unrelated reader or filesystem failure.
			throw new PluginException(LuceneGeoDocumentSchema.SCHEMA_MISMATCH_MESSAGE, e);
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
