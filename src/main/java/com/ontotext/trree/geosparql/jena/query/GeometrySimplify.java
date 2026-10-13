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

		List<Coordinate> occurrences = new ArrayList<>();
		// JTS copies Z but discards M. Private Z identifiers retain source occurrences through its 2D algorithm.
		Geometry identified = new GeometryEditor(source.getFactory()).edit(source,
				new GeometryEditor.CoordinateSequenceOperation() {
					@Override
					public CoordinateSequence edit(CoordinateSequence coordinates, Geometry geometry) {
						CustomCoordinateSequence result = new CustomCoordinateSequence(
								coordinates.size(), CoordinateSequenceDimensions.XYZ);
						for (int i = 0; i < coordinates.size(); i++) {
							result.setOrdinate(i, CoordinateSequence.X, coordinates.getX(i));
							result.setOrdinate(i, CoordinateSequence.Y, coordinates.getY(i));
							result.setOrdinate(i, CoordinateSequence.Z, occurrences.size());
							occurrences.add(coordinates.getCoordinateCopy(i));
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
			Coordinate original = occurrences.get(index);
			if (original.getX() != coordinate.getX() || original.getY() != coordinate.getY()) {
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
		Map<Vertex, Coordinate> originals = new HashMap<>();
		Set<Vertex> ambiguous = new HashSet<>();
		for (Coordinate coordinate : source.getCoordinates()) {
			Vertex vertex = new Vertex(coordinate.getX(), coordinate.getY(), coordinate.getZ());
			Coordinate previous = originals.putIfAbsent(vertex, coordinate);
			if (previous != null && Double.compare(previous.getM(), coordinate.getM()) != 0) {
				ambiguous.add(vertex);
			}
		}
		return restoreCoordinates(simplified, coordinate -> {
			Vertex vertex = new Vertex(coordinate.getX(), coordinate.getY(), coordinate.getZ());
			Coordinate original = originals.get(vertex);
			if (original == null || ambiguous.contains(vertex)) {
				throw new IllegalArgumentException("Simplification cannot preserve source Z/M ordinates");
			}
			return original;
		});
	}

	private static Geometry restoreCoordinates(Geometry simplified, Function<Coordinate, Coordinate> lookup) {
		return new GeometryEditor(simplified.getFactory()).edit(simplified,
				new GeometryEditor.CoordinateSequenceOperation() {
					@Override
					public CoordinateSequence edit(CoordinateSequence coordinates, Geometry geometry) {
						if (coordinates.size() == 0) {
							return coordinates.copy();
						}
						double[] x = new double[coordinates.size()];
						double[] y = new double[coordinates.size()];
						double[] z = new double[coordinates.size()];
						double[] m = new double[coordinates.size()];
						for (int i = 0; i < coordinates.size(); i++) {
							Coordinate original = lookup.apply(coordinates.getCoordinateCopy(i));
							x[i] = original.getX();
							y[i] = original.getY();
							z[i] = original.getZ();
							m[i] = original.getM();
						}
						return new CustomCoordinateSequence(x, y, z, m);
					}
				});
	}

	private record Vertex(double x, double y, double z) {
	}
}
