package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;
import org.apache.jena.geosparql.implementation.jts.CustomCoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Selects a direct geometry member using a one-based index.
 */
public final class GeometryMember {
	private GeometryMember() {
	}

	public static GeometryWrapper calculate(GeometryWrapper geometry, int index) {
		Geometry parsingGeometry = geometry.getParsingGeometry();
		if (index < 1 || index > parsingGeometry.getNumGeometries()) {
			throw new IllegalArgumentException("Geometry member index is out of range: " + index);
		}

		Geometry member = parsingGeometry.getGeometryN(index - 1);
		DimensionInfo dimensions = new DimensionInfo(memberDimensions(geometry, member), member.getDimension());
		return new GeometryWrapper(member, geometry.getSrsURI(), geometry.getGeometryDatatypeURI(), dimensions);
	}

	private static CoordinateSequenceDimensions memberDimensions(GeometryWrapper source, Geometry member) {
		CoordinateSequenceDimensions sourceDimensions = source.getDimensionInfo().getDimensions();
		return geometryDimensions(member, sourceDimensions);
	}

	private static CoordinateSequenceDimensions geometryDimensions(Geometry geometry,
			CoordinateSequenceDimensions fallback) {
		if (geometry instanceof Point point) {
			return coordinateDimensions(point.getCoordinateSequence());
		}
		if (geometry instanceof LineString lineString) {
			return coordinateDimensions(lineString.getCoordinateSequence());
		}
		if (geometry instanceof Polygon polygon) {
			return coordinateDimensions(polygon.getExteriorRing().getCoordinateSequence());
		}
		if (geometry instanceof GeometryCollection collection && collection.getNumGeometries() > 0) {
			return geometryDimensions(collection.getGeometryN(0), fallback);
		}
		return fallback;
	}

	private static CoordinateSequenceDimensions coordinateDimensions(CoordinateSequence coordinates) {
		return CustomCoordinateSequence.findCoordinateSequenceDimensions(
				coordinates.getDimension(), coordinates.getDimension() - coordinates.getMeasures());
	}
}
