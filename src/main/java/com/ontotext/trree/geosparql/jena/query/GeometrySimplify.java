package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;
import org.apache.jena.geosparql.implementation.jts.CustomCoordinateSequence;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryComponentFilter;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.GeometryEditor;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Applies Douglas-Peucker simplification in the source CRS coordinate units. */
public final class GeometrySimplify {
	private GeometrySimplify() {
	}

	public static GeometryWrapper calculate(GeometryWrapper geometry, double tolerance) {
		if (!Double.isFinite(tolerance) || tolerance < 0) {
			throw new IllegalArgumentException("Simplification tolerance must be finite and nonnegative");
		}
		// Source coordinate order avoids Jena's XY axis reversal, which drops Z and M.
		Geometry simplified = simplifySource(geometry.getParsingGeometry(), tolerance);
		DimensionInfo dimensions = new DimensionInfo(
				geometry.getDimensionInfo().getDimensions(), simplified.getDimension());
		return new GeometryWrapper(simplified, geometry.getSrsURI(),
				geometry.getGeometryDatatypeURI(), dimensions);
	}

	private static Geometry simplifySource(Geometry source, double tolerance) {
		if (source.isEmpty()) {
			return DouglasPeuckerSimplifier.simplify(source, tolerance);
		}
		if (source.getClass() == GeometryCollection.class) {
			List<Geometry> members = new ArrayList<>();
			for (int i = 0; i < source.getNumGeometries(); i++) {
				Geometry member = simplifySource(source.getGeometryN(i), tolerance);
				if (!member.isEmpty()) {
					members.add(member);
				}
			}
			return source.getFactory().createGeometryCollection(members.toArray(Geometry[]::new));
		}
		if (!hasAdditionalOrdinates(source)) {
			return DouglasPeuckerSimplifier.simplify(source, tolerance);
		}

		List<SourceOccurrence> occurrences = new ArrayList<>();
		// JTS copies Z but discards M. Private Z identifiers retain source occurrences through its 2D algorithm.
		Geometry identified = new GeometryEditor(source.getFactory()).edit(source,
				new GeometryEditor.CoordinateSequenceOperation() {
					@Override
					public CoordinateSequence edit(CoordinateSequence coordinates, Geometry geometry) {
						CustomCoordinateSequence result = new CustomCoordinateSequence(
								coordinates.size(), CoordinateSequenceDimensions.XYZ);
						CoordinateSequenceDimensions dimensions = coordinateDimensions(coordinates);
						for (int i = 0; i < coordinates.size(); i++) {
							result.setOrdinate(i, CoordinateSequence.X, coordinates.getX(i));
							result.setOrdinate(i, CoordinateSequence.Y, coordinates.getY(i));
							result.setOrdinate(i, CoordinateSequence.Z, occurrences.size());
							occurrences.add(new SourceOccurrence(coordinates.getCoordinateCopy(i), dimensions));
						}
						return result;
					}
				});
		DouglasPeuckerSimplifier simplifier = new DouglasPeuckerSimplifier(identified);
		simplifier.setDistanceTolerance(tolerance);
		simplifier.setEnsureValid(false);
		Geometry simplified = simplifier.getResultGeometry();
		if ((source instanceof Polygon || source instanceof MultiPolygon)
				&& (simplified.getDimension() != 2 || !simplified.isValid())) {
			// Repair can reorder or create vertices; only uniquely recoverable values are safe in that case.
			return restoreRepairOrdinates(source, DouglasPeuckerSimplifier.simplify(source, tolerance));
		}
		return restoreCoordinates(simplified, coordinate -> {
			double identifier = coordinate.getZ();
			int index = (int) identifier;
			if (index != identifier || index < 0 || index >= occurrences.size()) {
				throw new IllegalArgumentException("Simplification cannot preserve source Z/M ordinates");
			}
			SourceOccurrence original = occurrences.get(index);
			if (original.coordinate().getX() != coordinate.getX()
					|| original.coordinate().getY() != coordinate.getY()) {
				throw new IllegalArgumentException("Simplification cannot preserve source Z/M ordinates");
			}
			return original;
		});
	}

	private static boolean hasAdditionalOrdinates(Geometry geometry) {
		boolean[] additional = {false};
		geometry.apply((GeometryComponentFilter) component -> {
			CoordinateSequence coordinates = component instanceof Point point ? point.getCoordinateSequence()
					: component instanceof LineString line ? line.getCoordinateSequence() : null;
			if (coordinates != null && (coordinates.hasZ() || coordinates.hasM())) {
				additional[0] = true;
			}
		});
		return additional[0];
	}

	private static Geometry restoreRepairOrdinates(Geometry source, Geometry simplified) {
		Map<Vertex, SourceOccurrence> originals = new HashMap<>();
		Set<Vertex> ambiguous = new HashSet<>();
		source.apply((GeometryComponentFilter) component -> {
			CoordinateSequence coordinates = component instanceof Point point ? point.getCoordinateSequence()
					: component instanceof LineString line ? line.getCoordinateSequence() : null;
			if (coordinates == null) {
				return;
			}
			CoordinateSequenceDimensions dimensions = coordinateDimensions(coordinates);
			for (int i = 0; i < coordinates.size(); i++) {
				Coordinate coordinate = coordinates.getCoordinateCopy(i);
				Vertex vertex = new Vertex(coordinate.getX(), coordinate.getY(), coordinate.getZ());
				SourceOccurrence previous = originals.putIfAbsent(vertex, new SourceOccurrence(coordinate, dimensions));
				if (previous != null && (Double.compare(previous.coordinate().getM(), coordinate.getM()) != 0
						|| previous.dimensions() != dimensions)) {
					ambiguous.add(vertex);
				}
			}
		});
		return restoreCoordinates(simplified, coordinate -> {
			Vertex vertex = new Vertex(coordinate.getX(), coordinate.getY(), coordinate.getZ());
			SourceOccurrence original = originals.get(vertex);
			if (original == null || ambiguous.contains(vertex)) {
				throw new IllegalArgumentException("Simplification cannot preserve source Z/M ordinates");
			}
			return original;
		});
	}

	private static Geometry restoreCoordinates(Geometry simplified, Function<Coordinate, SourceOccurrence> lookup) {
		return new GeometryEditor(simplified.getFactory()).edit(simplified,
				new GeometryEditor.CoordinateSequenceOperation() {
					@Override
					public CoordinateSequence edit(CoordinateSequence coordinates, Geometry geometry) {
						if (coordinates.size() == 0) {
							return coordinates.copy();
						}
						SourceOccurrence first = lookup.apply(coordinates.getCoordinateCopy(0));
						// Undefined Z/M values do not remove their declared axes.
						CustomCoordinateSequence result = new CustomCoordinateSequence(coordinates.size(), first.dimensions());
						for (int i = 0; i < coordinates.size(); i++) {
							SourceOccurrence original = i == 0 ? first : lookup.apply(coordinates.getCoordinateCopy(i));
							if (original.dimensions() != first.dimensions()) {
								throw new IllegalArgumentException("Simplification cannot preserve source coordinate layout");
							}
							result.setOrdinate(i, CoordinateSequence.X, original.coordinate().getX());
							result.setOrdinate(i, CoordinateSequence.Y, original.coordinate().getY());
							result.setOrdinate(i, CoordinateSequence.Z, original.coordinate().getZ());
							result.setOrdinate(i, CoordinateSequence.M, original.coordinate().getM());
						}
						return result;
					}
				});
	}

	private static CoordinateSequenceDimensions coordinateDimensions(CoordinateSequence coordinates) {
		return CustomCoordinateSequence.findCoordinateSequenceDimensions(
				coordinates.getDimension(), coordinates.getDimension() - coordinates.getMeasures());
	}

	private record SourceOccurrence(Coordinate coordinate, CoordinateSequenceDimensions dimensions) {
	}

	private record Vertex(double x, double y, double z) {
	}
}
