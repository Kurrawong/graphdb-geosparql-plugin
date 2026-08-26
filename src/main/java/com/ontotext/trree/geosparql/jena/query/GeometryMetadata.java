package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;

/**
 * Derives coordinate-layout metadata from a geometry.
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

	public static String simpleFeaturesTypeUri(GeometryWrapper geometry) {
		String geometryType = geometry.getGeometryType();
		return switch (geometryType) {
			case "Point", "LineString", "Polygon", "MultiPoint", "MultiLineString",
					"MultiPolygon", "GeometryCollection" -> SIMPLE_FEATURES_NAMESPACE + geometryType;
			default -> throw new IllegalArgumentException(
					"Unsupported Simple Features geometry type: " + geometryType);
		};
	}
}
