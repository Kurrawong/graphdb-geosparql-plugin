package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.GeometryDatatype;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;
import org.apache.jena.geosparql.implementation.registry.SRSRegistry;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.io.WKTWriter;

/**
 * Central RDF4J-to-Jena conversion entry point for geometry values.
 *
 * <p>The adapter initializes Jena's geometry registries, preserves RDF literal datatype and lexical form in
 * {@link SourceGeometryLiteral}, and derives the CRS84 {@link IndexGeometry} value used by Lucene.
 * Exact relation semantics remain in the Jena evaluator rather than this conversion layer.
 */
public final class JenaGeometryAdapter {
	private JenaGeometryAdapter() {
	}

	public static void initialize() {
		JenaCalculationPrecision.configure();
		SRSRegistry.setupDefaultSRS();
		GeometryDatatype.registerDatatypes();
		TypeMapper.getInstance().registerDatatype(GeoJsonGeometryDatatype.INSTANCE);
	}

	public static SourceGeometryLiteral toSourceGeometryLiteral(Literal literal) {
		initialize();
		return SourceGeometryLiteral.fromLiteral(literal);
	}

	public static SourceGeometryLiteral toSourceGeometryLiteral(Literal literal, IRI fallbackDatatype) {
		initialize();
		return SourceGeometryLiteral.fromLiteral(literal, fallbackDatatype);
	}

	public static SourceGeometryLiteral toSourceGeometryLiteral(Value value, boolean acceptNoType) {
		initialize();
		return SourceGeometryLiteral.fromValue(value, acceptNoType);
	}

	public static IndexGeometry toIndexGeometry(SourceGeometryLiteral sourceGeometryLiteral) {
		initialize();
		return IndexGeometry.fromSourceGeometryLiteral(sourceGeometryLiteral);
	}

	public static Literal toRdf4jLiteral(ValueFactory valueFactory,
			org.apache.jena.geosparql.implementation.GeometryWrapper wrapper, IRI datatype) {
		IRI jenaDatatype = SourceGeometryLiteral.normalizeDatatype(datatype);
		if (GeoConstants.GEO_WKT_LITERAL.equals(jenaDatatype)) {
			String wkt = new WKTWriter().write(wrapper.getParsingGeometry());
			if (!SRS_URI.DEFAULT_WKT_CRS84.equals(wrapper.getSrsURI())) {
				wkt = "<" + wrapper.getSrsURI() + "> " + wkt;
			}
			return valueFactory.createLiteral(wkt, datatype);
		}
		if (GeoConstants.GEO_JSON_LITERAL.equals(jenaDatatype)) {
			return toGeoJsonLiteral(valueFactory, wrapper, 2);
		}
		if (GeoConstants.GEO_GML_LITERAL.equals(jenaDatatype) && wrapper.isEmpty()) {
			return valueFactory.createLiteral("", datatype);
		}
		org.apache.jena.rdf.model.Literal literal = wrapper.asLiteral(jenaDatatype.stringValue());
		return valueFactory.createLiteral(literal.getLexicalForm(), datatype);
	}

	/**
	 * Serializes a query-function geometry result under the provider's GeoJSON dimensionality contract.
	 */
	public static Literal toQueryGeometryLiteral(ValueFactory valueFactory, GeometryWrapper source,
			GeometryWrapper result, IRI datatype,
			GeoJsonResultDimensionPolicy geoJsonResultDimensionPolicy) {
		result = normalizeQueryGeometryType(result);
		SourceGeometryLiteral.validateGeometryWrapper(result);
		requireRepresentableGeometryType(result);
		IRI jenaDatatype = SourceGeometryLiteral.normalizeDatatype(datatype);
		if (GeoConstants.GEO_WKT_LITERAL.equals(jenaDatatype)) {
			Literal literal = toQueryWktLiteral(valueFactory, result);
			requireRoundTrippableWktResult(result, literal);
			return valueFactory.createLiteral(literal.stringValue(), datatype);
		}
		if (GeoConstants.GEO_GML_LITERAL.equals(jenaDatatype)) {
			Literal literal = toGmlLiteral(valueFactory, result);
			requireRoundTrippableGeometryResult(literal);
			return valueFactory.createLiteral(literal.stringValue(), datatype);
		}
		if (GeoConstants.GEO_JSON_LITERAL.equals(jenaDatatype)) {
			if (!SRS_URI.DEFAULT_WKT_CRS84.equals(result.getSrsURI())) {
				throw new JenaGeoSparqlException("GeoJSON output requires CRS84: " + result.getSrsURI());
			}
			return toGeoJsonLiteral(valueFactory, result,
					geoJsonCoordinateDimension(source, result, geoJsonResultDimensionPolicy));
		}
		throw new JenaGeoSparqlException("Unsupported GeoSPARQL geometry datatype: " + datatype);
	}

	private static Literal toQueryWktLiteral(ValueFactory valueFactory, GeometryWrapper result) {
		if (isStructuredEmptyGeometryCollection(result.getParsingGeometry())) {
			return toStructuredEmptyWktLiteral(valueFactory, result);
		}
		Literal literal = toWktLiteral(valueFactory, result);
		if (!result.isEmpty()) {
			return literal;
		}
		String dimensionMarker = CoordinateSequenceDimensions.convertDimensions(
				result.getDimensionInfo().getDimensions());
		if (dimensionMarker.isEmpty() || literal.stringValue().endsWith(dimensionMarker + " EMPTY")) {
			return literal;
		}
		if (!literal.stringValue().endsWith(" EMPTY")) {
			throw new JenaGeoSparqlException(
					"Geometry result cannot represent its required coordinate layout");
		}
		String lexicalForm = literal.stringValue();
		return valueFactory.createLiteral(
				lexicalForm.substring(0, lexicalForm.length() - " EMPTY".length())
						+ dimensionMarker + " EMPTY",
				GeoConstants.GEO_WKT_LITERAL);
	}

	private static Literal toStructuredEmptyWktLiteral(ValueFactory valueFactory,
			GeometryWrapper result) {
		String lexicalForm = new WKTWriter().write(result.getParsingGeometry());
		String dimensionMarker = CoordinateSequenceDimensions.convertDimensions(
				result.getDimensionInfo().getDimensions());
		if (!dimensionMarker.isEmpty()) {
			int geometryTypeEnd = lexicalForm.indexOf(' ');
			if (geometryTypeEnd < 0) {
				throw new JenaGeoSparqlException(
						"Geometry result cannot represent its required coordinate layout");
			}
			lexicalForm = lexicalForm.substring(0, geometryTypeEnd) + dimensionMarker
					+ lexicalForm.substring(geometryTypeEnd);
		}
		if (!SRS_URI.DEFAULT_WKT_CRS84.equals(result.getSrsURI())) {
			lexicalForm = "<" + result.getSrsURI() + "> " + lexicalForm;
		}
		return valueFactory.createLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL);
	}

	private static boolean isStructuredEmptyGeometryCollection(Geometry geometry) {
		return geometry.isEmpty()
				&& "GeometryCollection".equals(geometry.getGeometryType())
				&& geometry.getNumGeometries() > 0;
	}

	private static void requireRoundTrippableWktResult(GeometryWrapper expected, Literal literal) {
		GeometryWrapper actual;
		try {
			actual = SourceGeometryLiteral.fromLiteral(literal).asGeometryWrapper();
		} catch (JenaGeoSparqlException e) {
			throw new JenaGeoSparqlException(
					"Geometry result cannot represent its required coordinate layout", e);
		}
		DimensionInfo expectedDimensions = expected.getDimensionInfo();
		DimensionInfo actualDimensions = actual.getDimensionInfo();
		if (expectedDimensions.getCoordinate() != actualDimensions.getCoordinate()
				|| expectedDimensions.getSpatial() != actualDimensions.getSpatial()) {
			throw new JenaGeoSparqlException(
					"Geometry result cannot represent its required coordinate layout");
		}
		if (!hasSameGeometryStructure(expected.getParsingGeometry(), actual.getParsingGeometry())) {
			throw new JenaGeoSparqlException(
					"Geometry result cannot represent its required structure");
		}
	}

	private static boolean hasSameGeometryStructure(Geometry expected, Geometry actual) {
		if (!expected.getGeometryType().equals(actual.getGeometryType())
				|| expected.isEmpty() != actual.isEmpty()) {
			return false;
		}
		if (!"GeometryCollection".equals(expected.getGeometryType())) {
			return true;
		}
		GeometryCollection expectedCollection = (GeometryCollection) expected;
		GeometryCollection actualCollection = (GeometryCollection) actual;
		if (expectedCollection.getNumGeometries() != actualCollection.getNumGeometries()) {
			return false;
		}
		for (int i = 0; i < expectedCollection.getNumGeometries(); i++) {
			if (!hasSameGeometryStructure(expectedCollection.getGeometryN(i),
					actualCollection.getGeometryN(i))) {
				return false;
			}
		}
		return true;
	}

	private static int geoJsonCoordinateDimension(GeometryWrapper source, GeometryWrapper result,
			GeoJsonResultDimensionPolicy policy) {
		if (result.isEmpty()) {
			return 2;
		}
		DimensionInfo resultDimensions = result.getDimensionInfo();
		requireUnmeasuredGeoJsonLayout(resultDimensions);
		if (policy == GeoJsonResultDimensionPolicy.XY_ONLY) {
			return 2;
		}
		DimensionInfo sourceDimensions = source.getDimensionInfo();
		requireUnmeasuredGeoJsonLayout(sourceDimensions);
		ActualCoordinateLayout sourceLayout = actualCoordinateLayout(source.getParsingGeometry());
		if (sourceLayout == ActualCoordinateLayout.MIXED) {
			throw new JenaGeoSparqlException("GeoJSON source has inconsistent Z ordinates");
		}
		if (sourceLayout != ActualCoordinateLayout.XYZ) {
			return 2;
		}
		if (sourceDimensions.getCoordinate() != 3
				|| resultDimensions.getCoordinate() != 3
				|| actualCoordinateLayout(result.getParsingGeometry()) != ActualCoordinateLayout.XYZ) {
			throw new JenaGeoSparqlException(
					"GeoJSON result does not preserve required source altitude");
		}
		return 3;
	}

	private static void requireUnmeasuredGeoJsonLayout(DimensionInfo dimensions) {
		if (dimensions.getCoordinate() != dimensions.getSpatial()
				|| dimensions.getCoordinate() > 3) {
			throw new JenaGeoSparqlException(
					"GeoJSON output does not support measured coordinate layouts");
		}
	}

	private static ActualCoordinateLayout actualCoordinateLayout(Geometry geometry) {
		CoordinateLayoutFilter filter = new CoordinateLayoutFilter();
		geometry.apply(filter);
		return filter.layout();
	}

	private enum ActualCoordinateLayout {
		EMPTY,
		XY,
		XYZ,
		MIXED
	}

	private static final class CoordinateLayoutFilter implements CoordinateSequenceFilter {
		private boolean hasXy;
		private boolean hasXyz;

		@Override
		public void filter(CoordinateSequence sequence, int index) {
			boolean finiteZ = sequence.getDimension() - sequence.getMeasures() >= 3
					&& Double.isFinite(sequence.getZ(index));
			hasXy |= !finiteZ;
			hasXyz |= finiteZ;
		}

		@Override
		public boolean isDone() {
			return hasXy && hasXyz;
		}

		@Override
		public boolean isGeometryChanged() {
			return false;
		}

		private ActualCoordinateLayout layout() {
			if (hasXy && hasXyz) {
				return ActualCoordinateLayout.MIXED;
			}
			if (hasXyz) {
				return ActualCoordinateLayout.XYZ;
			}
			return hasXy ? ActualCoordinateLayout.XY : ActualCoordinateLayout.EMPTY;
		}
	}

	private static void requireRoundTrippableGeometryResult(Literal literal) {
		try {
			SourceGeometryLiteral.fromLiteral(literal).asGeometryWrapper();
		} catch (JenaGeoSparqlException e) {
			throw new JenaGeoSparqlException(
					"Geometry result cannot represent its required coordinate layout", e);
		}
	}

	private static GeometryWrapper normalizeQueryGeometryType(GeometryWrapper wrapper) {
		if (!(wrapper.getParsingGeometry() instanceof LinearRing ring)) {
			return wrapper;
		}
		Geometry lineString = ring.getFactory().createLineString(ring.getCoordinateSequence().copy());
		return new GeometryWrapper(lineString, wrapper.getSrsURI(), wrapper.getGeometryDatatypeURI(),
				wrapper.getDimensionInfo());
	}

	private static void requireRepresentableGeometryType(GeometryWrapper wrapper) {
		switch (wrapper.getParsingGeometry().getGeometryType()) {
			case "Point", "LineString", "Polygon", "MultiPoint", "MultiLineString", "MultiPolygon",
					"GeometryCollection" -> {
			}
			default -> throw new JenaGeoSparqlException("Unsupported geometry result type: "
					+ wrapper.getParsingGeometry().getGeometryType());
		}
	}

	static Literal toGeoJsonLiteral(ValueFactory valueFactory, GeometryWrapper wrapper,
			int coordinateDimension) {
		Geometry geometry = GeoJsonGeometryDatatype.normalizeCoordinateSequences(
				wrapper.getParsingGeometry(), coordinateDimension);
		GeometryWrapper output = new GeometryWrapper(geometry, SRS_URI.DEFAULT_WKT_CRS84,
				GeoConstants.GEO_JSON_LITERAL.stringValue(),
				new DimensionInfo(coordinateDimension, coordinateDimension, geometry.getDimension()));
		org.apache.jena.rdf.model.Literal literal = output.asLiteral(GeoConstants.GEO_JSON_LITERAL.stringValue());
		return valueFactory.createLiteral(literal.getLexicalForm(), GeoConstants.GEO_JSON_LITERAL);
	}

	static Literal toWktLiteral(ValueFactory valueFactory, GeometryWrapper wrapper) {
		org.apache.jena.rdf.model.Literal literal = wrapper.asLiteral(GeoConstants.GEO_WKT_LITERAL.stringValue());
		return valueFactory.createLiteral(literal.getLexicalForm(), GeoConstants.GEO_WKT_LITERAL);
	}

	static Literal toGmlLiteral(ValueFactory valueFactory, GeometryWrapper wrapper) {
		if (wrapper.isEmpty()) {
			return valueFactory.createLiteral("", GeoConstants.GEO_GML_LITERAL);
		}
		DimensionInfo dimensions = wrapper.getDimensionInfo();
		if (dimensions.getCoordinate() != dimensions.getSpatial()) {
			throw new JenaGeoSparqlException("GML output does not support measured coordinate layouts");
		}
		if (dimensions.getSpatial() == 3
				&& wrapper.getSrsInfo().getCrs().getCoordinateSystem().getDimension() != 3) {
			throw new JenaGeoSparqlException(
					"GML XYZ output requires a three-dimensional source CRS: " + wrapper.getSrsURI());
		}
		org.apache.jena.rdf.model.Literal literal = wrapper.asLiteral(GeoConstants.GEO_GML_LITERAL.stringValue());
		return valueFactory.createLiteral(literal.getLexicalForm(), GeoConstants.GEO_GML_LITERAL);
	}
}
