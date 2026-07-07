package com.ontotext.trree.geosparql.lucene;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.sdk.PluginException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.spatial.SpatialStrategy;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.spatial4j.context.jts.JtsSpatialContext;
import org.locationtech.spatial4j.shape.jts.JtsGeometry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lucene GeoSPARQL document schema v2.
 */
final class LuceneGeoDocumentSchema {
	static final int SCHEMA_VERSION = 2;
	static final String FIELD_ID = "id";
	static final String FIELD_SPATIAL_PREFIX = "geoData1";
	static final String FIELD_SPATIAL_DV = "geoData2";
	static final String FIELD_INDEX_GEOMETRY = "geoData";
	static final String FIELD_SCHEMA_VERSION = "geoSchemaVersion";
	static final String FIELD_SOURCE_LEXICAL_FORM = "geoExactLexicalForm";
	static final String FIELD_SOURCE_DATATYPE = "geoExactDatatype";
	static final String FIELD_SOURCE_CRS = "geoExactCrs";
	static final String FIELD_INDEX_CRS = "geoIndexCrs";
	static final String FIELD_INDEX_BUILD_MODE = "geoIndexBuildMode";
	static final String COMMIT_SCHEMA_VERSION_KEY = "geosparql.luceneSchemaVersion";
	static final String COMMIT_SCHEMA_VERSION_VALUE = Integer.toString(SCHEMA_VERSION);
	static final String SCHEMA_MISMATCH_MESSAGE = "Existing GeoSPARQL Lucene index does not match current schema v2. "
			+ "Jena-backed CRS-correct evaluation requires a full GeoSPARQL reindex. "
			+ "Queries are unavailable until reindex completes; run the documented force-reindex control or command.";

	private static final FieldType INDEX_GEOMETRY_FIELD_TYPE = new FieldType();

	static {
		INDEX_GEOMETRY_FIELD_TYPE.setStored(true);
		INDEX_GEOMETRY_FIELD_TYPE.setIndexOptions(IndexOptions.NONE);
		INDEX_GEOMETRY_FIELD_TYPE.freeze();
	}

	private LuceneGeoDocumentSchema() {
	}

	static Document toDocument(long entityId, IndexGeometry geometry, SpatialStrategy strategy, JtsSpatialContext ctx) {
		Document doc = new Document();
		doc.add(new LongPoint(FIELD_ID, entityId));
		doc.add(new StoredField(FIELD_ID, entityId));
		doc.add(new NumericDocValuesField(FIELD_ID, entityId));

		JtsGeometry shape = new JtsGeometry(geometry.indexGeometry(), ctx, true, true);
		shape.index();

		for (Field field : strategy.createIndexableFields(shape)) {
			doc.add(field);
		}

		doc.add(indexGeometryField(geometry.indexGeometry()));
		doc.add(new StoredField(FIELD_SCHEMA_VERSION, SCHEMA_VERSION));
		doc.add(new StoredField(FIELD_SOURCE_LEXICAL_FORM, geometry.sourceGeometryLiteral().lexicalForm()));
		doc.add(new StoredField(FIELD_SOURCE_DATATYPE, geometry.sourceGeometryLiteral().datatype().stringValue()));
		doc.add(new StoredField(FIELD_SOURCE_CRS, geometry.sourceGeometryLiteral().effectiveCrsUri()));
		doc.add(new StoredField(FIELD_INDEX_CRS, geometry.indexCrs()));
		doc.add(new StoredField(FIELD_INDEX_BUILD_MODE, geometry.indexBuildMode()));

		return doc;
	}

	static IndexGeometry indexGeometry(Document doc) {
		assertCurrentSchemaDocument(doc);
		IndexableField field = doc.getField(FIELD_INDEX_GEOMETRY);
		byte[] bytes = Arrays.copyOfRange(field.binaryValue().bytes, field.binaryValue().offset,
				field.binaryValue().offset + field.binaryValue().length);
		try {
			return IndexGeometry.fromStoredMetadata(
					fieldValueToGeometry(bytes),
					doc.get(FIELD_SOURCE_LEXICAL_FORM),
					doc.get(FIELD_SOURCE_DATATYPE),
					doc.get(FIELD_SOURCE_CRS),
					doc.get(FIELD_INDEX_CRS),
					doc.get(FIELD_INDEX_BUILD_MODE));
		} catch (RuntimeException e) {
			throw new PluginException(SCHEMA_MISMATCH_MESSAGE, e);
		}
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
				&& doc.getField(FIELD_INDEX_GEOMETRY) != null
				&& doc.getField(FIELD_INDEX_GEOMETRY).binaryValue() != null
				&& doc.get(FIELD_SOURCE_LEXICAL_FORM) != null
				&& doc.get(FIELD_SOURCE_DATATYPE) != null
				&& doc.get(FIELD_SOURCE_CRS) != null
				&& IndexGeometry.INDEX_CRS.equals(doc.get(FIELD_INDEX_CRS))
				&& IndexGeometry.BUILD_MODE_TRANSFORMED_GEOMETRY.equals(doc.get(FIELD_INDEX_BUILD_MODE));
	}

	static void assertCurrentSchemaDocument(Document doc) {
		if (!isCurrentSchemaDocument(doc)) {
			throw new PluginException(SCHEMA_MISMATCH_MESSAGE);
		}
	}

	static boolean hasCurrentSchemaCommitData(Map<String, String> commitData) {
		return COMMIT_SCHEMA_VERSION_VALUE.equals(commitData.get(COMMIT_SCHEMA_VERSION_KEY));
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
		return commitData.entrySet();
	}

	static Query entityIdQuery(long entityId) {
		return LongPoint.newExactQuery(FIELD_ID, entityId);
	}

	static Query allDocumentsQuery() {
		return new MatchAllDocsQuery();
	}

	private static Field indexGeometryField(Geometry geometry) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(geometry);
			oos.close();
			return new Field(FIELD_INDEX_GEOMETRY, bos.toByteArray(), INDEX_GEOMETRY_FIELD_TYPE);
		} catch (Exception e) {
			throw new PluginException("Unable to create field from geometry.", e);
		}
	}

	private static Geometry fieldValueToGeometry(byte[] fieldValue) {
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(fieldValue);
			ObjectInputStream ois = new ObjectInputStream(bis);
			return (Geometry) ois.readObject();
		} catch (Exception e) {
			throw new PluginException("Unable to create geometry from field value.", e);
		}
	}
}
