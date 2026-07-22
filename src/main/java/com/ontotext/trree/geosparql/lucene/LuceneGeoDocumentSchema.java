package com.ontotext.trree.geosparql.lucene;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.sdk.PluginException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.shape.jts.JtsGeometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lucene GeoSPARQL document schema v2.
 */
final class LuceneGeoDocumentSchema {
	/** Per-document schema number; schema v2 remains unpublished and is rebuilt after incompatible iterations. */
	static final int SCHEMA_VERSION = 2;
	/** GraphDB entity id, stored for reconstruction and indexed/doc-valued for lookup and grouping. */
	static final String FIELD_ID = "id";
	/** Indexed terms for one complete CRS84 geometry or collection envelope; absent on empty sentinels. */
	static final String FIELD_SPATIAL_PREFIX = "geoData1";
	/** Binary doc values for the same complete geometry or envelope, used to verify candidates and reload bound inputs. */
	static final String FIELD_INDEX_GEOMETRY = "geoData2";
	/** Stored WKB for the complete native-CRS source geometry used to construct Jena exact-evaluation input. */
	static final String FIELD_SOURCE_GEOMETRY = "geoData";
	/** Stored per-document schema number used for defensive validation after the commit-level check. */
	static final String FIELD_SCHEMA_VERSION = "geoSchemaVersion";
	/** Stored authoritative RDF lexical form; the compatibility name does not denote a CRS84 geometry. */
	static final String FIELD_SOURCE_LEXICAL_FORM = "geoExactLexicalForm";
	/** Stored original RDF datatype IRI used to recover the source geometry literal's semantics. */
	static final String FIELD_SOURCE_DATATYPE = "geoExactDatatype";
	/** Stored effective source CRS URI associated with the native-CRS WKB and exact evaluation. */
	static final String FIELD_SOURCE_CRS = "geoExactCrs";
	/** Stored source coordinate dimension used to reconstruct dimension metadata, including for empty geometries. */
	static final String FIELD_SOURCE_COORDINATE_DIMENSION = "geoSourceCoordinateDimension";
	/** Stored source spatial dimension used with the coordinate dimension to distinguish XYZ from XYM. */
	static final String FIELD_SOURCE_SPATIAL_DIMENSION = "geoSourceSpatialDimension";
	/** Stored source topological dimension used to preserve point, line, area, and collection semantics. */
	static final String FIELD_SOURCE_TOPOLOGICAL_DIMENSION = "geoSourceTopologicalDimension";
	/** Stored CRS URI for the derived index geometry in geoData1 and geoData2; currently always CRS84. */
	static final String FIELD_INDEX_CRS = "geoIndexCrs";
	/** Indexed/stored marker distinguishing complete geometries, collection envelopes, and empty sentinels. */
	static final String FIELD_INDEX_BUILD_MODE = "geoIndexBuildMode";
	/** Commit metadata key used for the constant-time index-level schema compatibility check. */
	static final String COMMIT_SCHEMA_VERSION_KEY = "geosparql.luceneSchemaVersion";
	/** Commit metadata value written by the final unpublished schema-v2 layout. */
	static final String COMMIT_SCHEMA_VERSION_VALUE = Integer.toString(SCHEMA_VERSION);
	/** Commit metadata key distinguishing the final unpublished layout from earlier development-v2 indexes. */
	static final String COMMIT_SCHEMA_LAYOUT_KEY = "geosparql.luceneSchemaLayout";
	/** Commit metadata value requiring doc-values verification, source WKB, and one collection-envelope document. */
	static final String COMMIT_SCHEMA_LAYOUT_VALUE = "doc-values-source-wkb-collection-envelope";
	static final String SCHEMA_MISMATCH_MESSAGE = "Existing GeoSPARQL Lucene index does not match current schema v2. "
			+ "Jena-backed CRS-correct evaluation requires a full GeoSPARQL reindex. "
			+ "Queries are unavailable until reindex completes; run the documented force-reindex control or command.";

	private LuceneGeoDocumentSchema() {
	}

	static List<Document> toDocuments(long entityId, List<IndexGeometry> geometries,
			SpatialStrategy strategy, JtsSpatialContext ctx) {
		Map<SourceGeometryLiteral, IndexGeometry> distinctSources = new LinkedHashMap<>();
		for (IndexGeometry geometry : geometries) {
			distinctSources.putIfAbsent(geometry.sourceGeometryLiteral(), geometry);
		}
		List<Document> documents = new ArrayList<>(distinctSources.size());
		for (IndexGeometry geometry : distinctSources.values()) {
			documents.add(toDocument(entityId, geometry, strategy, ctx));
		}
		return documents;
	}

	static Document toDocument(long entityId, IndexGeometry geometry,
			SpatialStrategy strategy, JtsSpatialContext ctx) {
		SourceGeometryLiteral source = geometry.sourceGeometryLiteral();
		byte[] sourceGeometryWkb = SourceGeometryWkbCodec.encode(source);
		DimensionInfo dimensions = source.asGeometryWrapper().getDimensionInfo();

		Document doc = new Document();
		doc.add(new LongPoint(FIELD_ID, entityId));
		doc.add(new StoredField(FIELD_ID, entityId));
		doc.add(new NumericDocValuesField(FIELD_ID, entityId));

		if (geometry.isSpatialCandidate()) {
			JtsGeometry shape = new JtsGeometry(geometry.indexGeometry(), ctx, true, true);
			shape.index();

			for (Field field : strategy.createIndexableFields(shape)) {
				doc.add(field);
			}
		}
		// Empty source geometry literals still need an entity-bearing document so full scans can reconstruct
		// them for exact predicate evaluation. Their sentinel document deliberately has no Lucene spatial fields.

		doc.add(new StoredField(FIELD_SOURCE_GEOMETRY, sourceGeometryWkb));
		doc.add(new StoredField(FIELD_SCHEMA_VERSION, SCHEMA_VERSION));
		doc.add(new StoredField(FIELD_SOURCE_LEXICAL_FORM, source.lexicalForm()));
		doc.add(new StoredField(FIELD_SOURCE_DATATYPE, source.datatype().stringValue()));
		doc.add(new StoredField(FIELD_SOURCE_CRS, source.effectiveCrsUri()));
		doc.add(new StoredField(FIELD_SOURCE_COORDINATE_DIMENSION, dimensions.getCoordinate()));
		doc.add(new StoredField(FIELD_SOURCE_SPATIAL_DIMENSION, dimensions.getSpatial()));
		doc.add(new StoredField(FIELD_SOURCE_TOPOLOGICAL_DIMENSION, dimensions.getTopological()));
		doc.add(new StoredField(FIELD_INDEX_CRS, geometry.indexCrs()));
		doc.add(new StringField(FIELD_INDEX_BUILD_MODE, geometry.indexBuildMode(), Field.Store.YES));

		return doc;
	}

	static IndexGeometry indexGeometry(Document doc, SourceGeometryLiteral source,
			Geometry indexGeometry) {
		try {
			return IndexGeometry.fromStoredMetadata(
					indexGeometry,
					source,
					doc.get(FIELD_SOURCE_CRS),
					doc.get(FIELD_INDEX_CRS),
					doc.get(FIELD_INDEX_BUILD_MODE));
		} catch (RuntimeException e) {
			throw new PluginException(SCHEMA_MISMATCH_MESSAGE, e);
		}
	}

	static SourceGeometryLiteral sourceGeometryLiteral(Document doc, SourceGeometryLiteralResolver sourceResolver) {
		assertCurrentSchemaDocument(doc);
		byte[] sourceGeometryWkb = binaryFieldValue(doc, FIELD_SOURCE_GEOMETRY);
		DimensionInfo dimensions = sourceDimensionInfo(doc);
		StoredSourceGeometryIdentity identity = new StoredSourceGeometryIdentity(
				doc.get(FIELD_SOURCE_LEXICAL_FORM),
				doc.get(FIELD_SOURCE_DATATYPE),
				doc.get(FIELD_SOURCE_CRS));
		return sourceResolver.resolve(identity, sourceGeometryWkb, dimensions);
	}

	static long entityId(Document doc) {
		IndexableField field = doc.getField(FIELD_ID);
		if (field == null || field.numericValue() == null) {
			throw new PluginException("GeoSPARQL Lucene document is missing entity id.");
		}
		return field.numericValue().longValue();
	}

	static boolean isCurrentSchemaDocument(Document doc) {
		IndexableField schemaVersion = doc.getField(FIELD_SCHEMA_VERSION);
		return schemaVersion != null
				&& schemaVersion.numericValue() != null
				&& schemaVersion.numericValue().intValue() == SCHEMA_VERSION
				&& doc.getField(FIELD_SOURCE_GEOMETRY) != null
				&& doc.getField(FIELD_SOURCE_GEOMETRY).binaryValue() != null
				&& doc.get(FIELD_SOURCE_LEXICAL_FORM) != null
				&& doc.get(FIELD_SOURCE_DATATYPE) != null
				&& doc.get(FIELD_SOURCE_CRS) != null
				&& numericFieldValue(doc, FIELD_SOURCE_COORDINATE_DIMENSION) != null
				&& numericFieldValue(doc, FIELD_SOURCE_SPATIAL_DIMENSION) != null
				&& numericFieldValue(doc, FIELD_SOURCE_TOPOLOGICAL_DIMENSION) != null
				&& IndexGeometry.INDEX_CRS.equals(doc.get(FIELD_INDEX_CRS))
				&& isSupportedBuildMode(doc.get(FIELD_INDEX_BUILD_MODE));
	}

	private static boolean isSupportedBuildMode(String buildMode) {
		return IndexGeometry.BUILD_MODE_TRANSFORMED_GEOMETRY.equals(buildMode)
				|| IndexGeometry.BUILD_MODE_TRANSFORMED_ENVELOPE.equals(buildMode)
				|| IndexGeometry.BUILD_MODE_EMPTY_SENTINEL.equals(buildMode);
	}

	static void assertCurrentSchemaDocument(Document doc) {
		if (!isCurrentSchemaDocument(doc)) {
			throw new PluginException(SCHEMA_MISMATCH_MESSAGE);
		}
	}

	static boolean hasCurrentSchemaCommitData(Map<String, String> commitData) {
		return COMMIT_SCHEMA_VERSION_VALUE.equals(commitData.get(COMMIT_SCHEMA_VERSION_KEY))
				&& COMMIT_SCHEMA_LAYOUT_VALUE.equals(commitData.get(COMMIT_SCHEMA_LAYOUT_KEY));
	}

	static Iterable<Map.Entry<String, String>> currentSchemaCommitData(
			Iterable<Map.Entry<String, String>> existingCommitData) {
		Map<String, String> commitData = new LinkedHashMap<>();
		if (existingCommitData != null) {
			for (Map.Entry<String, String> entry : existingCommitData) {
				commitData.put(entry.getKey(), entry.getValue());
			}
		}
		commitData.put(COMMIT_SCHEMA_VERSION_KEY, COMMIT_SCHEMA_VERSION_VALUE);
		commitData.put(COMMIT_SCHEMA_LAYOUT_KEY, COMMIT_SCHEMA_LAYOUT_VALUE);
		return commitData.entrySet();
	}

	static Query entityIdQuery(long entityId) {
		return LongPoint.newExactQuery(FIELD_ID, entityId);
	}

	static Query allDocumentsQuery() {
		return new MatchAllDocsQuery();
	}

	private static DimensionInfo sourceDimensionInfo(Document doc) {
		Number coordinate = numericFieldValue(doc, FIELD_SOURCE_COORDINATE_DIMENSION);
		Number spatial = numericFieldValue(doc, FIELD_SOURCE_SPATIAL_DIMENSION);
		Number topological = numericFieldValue(doc, FIELD_SOURCE_TOPOLOGICAL_DIMENSION);
		if (coordinate == null || spatial == null || topological == null) {
			throw new PluginException(SCHEMA_MISMATCH_MESSAGE);
		}
		try {
			return new DimensionInfo(coordinate.intValue(), spatial.intValue(), topological.intValue());
		} catch (RuntimeException e) {
			throw new PluginException(SCHEMA_MISMATCH_MESSAGE, e);
		}
	}

	private static Number numericFieldValue(Document doc, String fieldName) {
		IndexableField field = doc.getField(fieldName);
		return field == null ? null : field.numericValue();
	}

	private static byte[] binaryFieldValue(Document doc, String fieldName) {
		IndexableField field = doc.getField(fieldName);
		if (field == null || field.binaryValue() == null) {
			throw new PluginException(SCHEMA_MISMATCH_MESSAGE);
		}
		return Arrays.copyOfRange(field.binaryValue().bytes, field.binaryValue().offset,
				field.binaryValue().offset + field.binaryValue().length);
	}

}
