package com.ontotext.trree.geosparql.jena.query;

import java.io.IOException;
import java.io.StringReader;

import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.GMLDatatype;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

/**
 * Derives coordinate-layout metadata and serialization-specific geometry types.
 */
public final class GeometryMetadata {
	private static final String SIMPLE_FEATURES_NAMESPACE = "http://www.opengis.net/ont/sf#";

	private GeometryMetadata() {
	}

	public static boolean is3D(GeometryWrapper geometry) {
		CoordinateSequenceDimensions dimensions = geometry.getDimensionInfo().getDimensions();
		return dimensions == CoordinateSequenceDimensions.XYZ
				|| dimensions == CoordinateSequenceDimensions.XYZM;
	}

	public static boolean isMeasured(GeometryWrapper geometry) {
		CoordinateSequenceDimensions dimensions = geometry.getDimensionInfo().getDimensions();
		return dimensions == CoordinateSequenceDimensions.XYM
				|| dimensions == CoordinateSequenceDimensions.XYZM;
	}

	public static String geometryTypeUri(GeometryWrapper geometry) {
		if (GMLDatatype.URI.equals(geometry.getGeometryDatatypeURI())) {
			return "http://www.opengis.net/ont/gml#" + gmlType(geometry.getLexicalForm());
		}
		String geometryType = geometry.getGeometryType();
		return switch (geometryType) {
			case "Point", "LineString", "Polygon", "MultiPoint", "MultiLineString",
					"MultiPolygon", "GeometryCollection" -> SIMPLE_FEATURES_NAMESPACE + geometryType;
			default -> throw new IllegalArgumentException(
					"Unsupported Simple Features geometry type: " + geometryType);
		};
	}

	/** Reads retained source GML, or the writer's GML for a constructed geometry. */
	private static String gmlType(String lexicalForm) {
		// Jena interprets the empty GML literal as an empty Point.
		if (lexicalForm.isEmpty()) {
			return "Point";
		}
		SAXBuilder builder = new SAXBuilder();
		builder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
		builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		builder.setExpandEntities(false);
		try {
			return builder.build(new StringReader(lexicalForm)).getRootElement().getName();
		} catch (JDOMException | IOException ex) {
			throw new DatatypeFormatException("Unable to read GML geometry type", ex);
		}
	}
}
