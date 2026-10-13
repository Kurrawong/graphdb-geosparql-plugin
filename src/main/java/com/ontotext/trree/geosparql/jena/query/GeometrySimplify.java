package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.jts.CustomCoordinateSequence;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.GeometryEditor;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Applies Douglas-Peucker simplification in the source CRS coordinate units. */
public final class GeometrySimplify {
	private GeometrySimplify() {
	}

	public static GeometryWrapper calculate(GeometryWrapper geometry, double tolerance) {
		if (!Double.isFinite(tolerance) || tolerance < 0) {
			throw new IllegalArgumentException("Simplification tolerance must be finite and nonnegative");
		}
		// Source coordinate order avoids Jena's XY axis reversal, which drops Z and M.
		Geometry source = geometry.getParsingGeometry();
		Geometry simplified = DouglasPeuckerSimplifier.simplify(source, tolerance);
		simplified = restoreOrdinates(source, simplified);
		DimensionInfo dimensions = new DimensionInfo(
				geometry.getDimensionInfo().getDimensions(), simplified.getDimension());
		return new GeometryWrapper(simplified, geometry.getSrsURI(),
				geometry.getGeometryDatatypeURI(), dimensions);
	}

	private static Geometry restoreOrdinates(Geometry source, Geometry simplified) {
		Coordinate[] sourceCoordinates = source.getCoordinates();
		if (Arrays.stream(sourceCoordinates).noneMatch(coordinate ->
				!Double.isNaN(coordinate.getZ()) || !Double.isNaN(coordinate.getM()))) {
			return simplified;
		}
		Map<Vertex, Double> sourceMeasures = new HashMap<>();
		Set<Vertex> ambiguous = new HashSet<>();
		for (Coordinate coordinate : sourceCoordinates) {
			Vertex vertex = new Vertex(coordinate.getX(), coordinate.getY(), coordinate.getZ());
			Double previous = sourceMeasures.putIfAbsent(vertex, coordinate.getM());
			if (previous != null && Double.compare(previous, coordinate.getM()) != 0) {
				ambiguous.add(vertex);
			}
		}
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
							x[i] = coordinates.getX(i);
							y[i] = coordinates.getY(i);
							z[i] = coordinates.getZ(i);
							Vertex vertex = new Vertex(x[i], y[i], z[i]);
							Double measure = sourceMeasures.get(vertex);
							// JTS drops M during vertex copying; polygon repair may also invent vertices or Z.
							if (measure == null || ambiguous.contains(vertex)) {
								throw new IllegalArgumentException("Simplification cannot preserve source Z/M ordinates");
							}
							m[i] = measure;
						}
						return new CustomCoordinateSequence(x, y, z, m);
					}
				});
	}

	private record Vertex(double x, double y, double z) {
	}
}
