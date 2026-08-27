package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.GeometryDatatype;
import org.apache.jena.geosparql.implementation.registry.SRSRegistry;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.locationtech.jts.geom.Geometry;
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
	 * Serializes a query-function geometry result without changing its datatype, CRS, or coordinate layout.
	 */
	public static Literal toQueryGeometryLiteral(ValueFactory valueFactory, GeometryWrapper wrapper, IRI datatype) {
		wrapper = normalizeQueryGeometryType(wrapper);
		SourceGeometryLiteral.validateGeometryWrapper(wrapper);
		requireRepresentableGeometryType(wrapper);
		IRI jenaDatatype = SourceGeometryLiteral.normalizeDatatype(datatype);
		if (GeoConstants.GEO_WKT_LITERAL.equals(jenaDatatype)) {
			Literal literal = toWktLiteral(valueFactory, wrapper);
			requireRoundTrippableGeometryResult(literal);
			return valueFactory.createLiteral(literal.stringValue(), datatype);
		}
		if (GeoConstants.GEO_GML_LITERAL.equals(jenaDatatype)) {
			Literal literal = toGmlLiteral(valueFactory, wrapper);
			requireRoundTrippableGeometryResult(literal);
			return valueFactory.createLiteral(literal.stringValue(), datatype);
		}
		if (GeoConstants.GEO_JSON_LITERAL.equals(jenaDatatype)) {
			if (!SRS_URI.DEFAULT_WKT_CRS84.equals(wrapper.getSrsURI())) {
				throw new JenaGeoSparqlException("GeoJSON output requires CRS84: " + wrapper.getSrsURI());
			}
			DimensionInfo dimensions = wrapper.getDimensionInfo();
			if (dimensions.getCoordinate() != dimensions.getSpatial()
					|| dimensions.getCoordinate() > 3) {
				throw new JenaGeoSparqlException(
						"GeoJSON output does not support measured coordinate layouts");
			}
			return toGeoJsonLiteral(valueFactory, wrapper, dimensions.getCoordinate());
		}
		throw new JenaGeoSparqlException("Unsupported GeoSPARQL geometry datatype: " + datatype);
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
