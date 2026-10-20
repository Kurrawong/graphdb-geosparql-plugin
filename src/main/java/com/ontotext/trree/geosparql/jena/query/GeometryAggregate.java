package com.ontotext.trree.geosparql.jena.query;

import com.ontotext.trree.geosparql.jena.JenaGeoSparqlException;
import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.GeometryReverse;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;
import org.apache.jena.geosparql.implementation.jts.CustomCoordinateSequence;
import org.apache.jena.geosparql.implementation.jts.CustomGeometryFactory;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.GeometryEditor;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Calculates GeoSPARQL spatial aggregates over the geometries of one group.
 */
public final class GeometryAggregate {
	private GeometryAggregate() {
	}

	/**
	 * Collects the geometries into one GeometryCollection in a shared SRS and geometry datatype, both chosen from
	 * every geometry in the group. Members keep the axis order of the shared SRS and the Z and M ordinates that every
	 * member has; a member with other ordinates loses them, so mixed coordinate layouts reduce to the shared ones.
	 * Empty geometries and empty collection members at any depth are omitted, keeping the remaining nesting and
	 * member types, so a group of only empty geometries gives an empty collection.
	 */
	public static GeometryWrapper collect(List<GeometryWrapper> geometries) {
		String srsUri = shared(geometries, GeometryWrapper::getSrsURI, SRS_URI.DEFAULT_WKT_CRS84);
		String datatypeUri = shared(geometries, GeometryWrapper::getGeometryDatatypeURI, WKTDatatype.URI);
		List<Geometry> members = geometries.stream()
				.filter(geometry -> !geometry.isEmpty())
				.map(geometry -> withoutEmptyMembers(inSrs(geometry, srsUri).getParsingGeometry()))
				.toList();
		CoordinateSequenceDimensions layout = sharedLayout(members);
		GeometryCollection collection = CustomGeometryFactory.theInstance().createGeometryCollection(
				members.stream().map(member -> withLayout(member, layout)).toArray(Geometry[]::new));
		return new GeometryWrapper(collection, srsUri, datatypeUri,
				new DimensionInfo(layout, collection.getDimension()));
	}

	/**
	 * Calculates the envelope of the collected geometries.
	 */
	public static GeometryWrapper boundingBox(List<GeometryWrapper> geometries) {
		return collect(geometries).envelope();
	}

	/**
	 * Calculates the convex hull of the collected geometries.
	 */
	public static GeometryWrapper convexHull(List<GeometryWrapper> geometries) {
		return collect(geometries).convexHull();
	}

	/**
	 * Calculates the centroid of the collected geometries.
	 */
	public static GeometryWrapper centroid(List<GeometryWrapper> geometries) {
		return GeometryCentroid.calculate(collect(geometries));
	}

	/**
	 * Calculates the bounding circle of the collected geometries.
	 */
	public static GeometryWrapper boundingCircle(List<GeometryWrapper> geometries) {
		return GeometryBoundingCircle.calculate(collect(geometries));
	}

	/**
	 * Calculates the concave hull of the collected geometries without a {@code targetPercent}, as
	 * {@code geof:concaveHull} applied to the collected geometries.
	 */
	public static GeometryWrapper concaveHull(List<GeometryWrapper> geometries) {
		return GeometryConcaveHull.calculate(collect(geometries));
	}

	/**
	 * Calculates the concave hull of the collected geometries. {@code targetPercent} is the maximum edge-length
	 * ratio, from zero for the tightest hull to one for the convex hull.
	 */
	public static GeometryWrapper concaveHull(List<GeometryWrapper> geometries, double targetPercent) {
		if (!(targetPercent >= 0.0 && targetPercent <= 1.0)) {
			throw new JenaGeoSparqlException("Concave hull targetPercent must be between 0 and 1: " + targetPercent);
		}
		return GeometryConcaveHull.calculate(collect(geometries), targetPercent);
	}

	/**
	 * Calculates the union of the collected geometries. Like {@code geof:union}, the result keeps the coordinate
	 * layout of the collected geometries, so ordinates the union cannot carry make the result unrepresentable.
	 */
	public static GeometryWrapper union(List<GeometryWrapper> geometries) {
		GeometryWrapper collected = collect(geometries);
		Geometry xyUnion = collected.getXYGeometry().union();
		DimensionInfo dimensions = collected.getDimensionInfo();
		return new GeometryWrapper(GeometryReverse.check(xyUnion, collected.getSrsURI()), collected.getSrsURI(),
				collected.getGeometryDatatypeURI(),
				new DimensionInfo(dimensions.getCoordinate(), dimensions.getSpatial(), xyUnion.getDimension()));
	}

	/**
	 * Removes empty members at every depth. The WKT result boundary rejects collections with empty members because
	 * Jena's WKT reader does not read them back faithfully.
	 */
	private static Geometry withoutEmptyMembers(Geometry geometry) {
		if (!(geometry instanceof GeometryCollection collection)) {
			return geometry;
		}
		List<Geometry> members = new ArrayList<>();
		boolean changed = false;
		for (int index = 0; index < collection.getNumGeometries(); index++) {
			Geometry original = collection.getGeometryN(index);
			Geometry member = withoutEmptyMembers(original);
			changed |= member != original;
			if (member.isEmpty()) {
				changed = true;
			} else {
				members.add(member);
			}
		}
		if (!changed) {
			return geometry;
		}
		GeometryFactory factory = CustomGeometryFactory.theInstance();
		if (collection instanceof MultiPoint) {
			return factory.createMultiPoint(members.toArray(new Point[0]));
		}
		if (collection instanceof MultiLineString) {
			return factory.createMultiLineString(members.toArray(new LineString[0]));
		}
		if (collection instanceof MultiPolygon) {
			return factory.createMultiPolygon(members.toArray(new Polygon[0]));
		}
		return factory.createGeometryCollection(members.toArray(new Geometry[0]));
	}

	/**
	 * Returns the Z and M ordinates present in every coordinate sequence, or XY when there are no members.
	 */
	private static CoordinateSequenceDimensions sharedLayout(List<Geometry> members) {
		boolean[] shared = {!members.isEmpty(), !members.isEmpty()};
		CoordinateSequenceFilter filter = new CoordinateSequenceFilter() {
			@Override
			public void filter(CoordinateSequence sequence, int index) {
				shared[0] &= sequence.hasZ();
				shared[1] &= sequence.hasM();
			}

			@Override
			public boolean isDone() {
				return !shared[0] && !shared[1];
			}

			@Override
			public boolean isGeometryChanged() {
				return false;
			}
		};
		members.forEach(member -> member.apply(filter));
		return layout(shared[0], shared[1]);
	}

	private static Geometry withLayout(Geometry geometry, CoordinateSequenceDimensions layout) {
		boolean z = layout == CoordinateSequenceDimensions.XYZ || layout == CoordinateSequenceDimensions.XYZM;
		boolean m = layout == CoordinateSequenceDimensions.XYM || layout == CoordinateSequenceDimensions.XYZM;
		GeometryEditor editor = new GeometryEditor(CustomGeometryFactory.theInstance());
		return editor.edit(geometry, new GeometryEditor.CoordinateSequenceOperation() {
			@Override
			public CoordinateSequence edit(CoordinateSequence source, Geometry component) {
				if (source.hasZ() == z && source.hasM() == m) {
					return source;
				}
				CustomCoordinateSequence target = new CustomCoordinateSequence(source.size(), layout);
				for (int index = 0; index < source.size(); index++) {
					target.setOrdinate(index, CoordinateSequence.X, source.getX(index));
					target.setOrdinate(index, CoordinateSequence.Y, source.getY(index));
					if (z) {
						target.setOrdinate(index, CoordinateSequence.Z, source.getZ(index));
					}
					if (m) {
						target.setOrdinate(index, CoordinateSequence.M, source.getM(index));
					}
				}
				return target;
			}
		});
	}

	private static CoordinateSequenceDimensions layout(boolean z, boolean m) {
		if (z) {
			return m ? CoordinateSequenceDimensions.XYZM : CoordinateSequenceDimensions.XYZ;
		}
		return m ? CoordinateSequenceDimensions.XYM : CoordinateSequenceDimensions.XY;
	}

	/**
	 * Returns the value every geometry shares, or the fallback when the values differ. Aggregate input has no
	 * defined order, so the first geometry cannot choose the result.
	 */
	private static String shared(List<GeometryWrapper> geometries, Function<GeometryWrapper, String> value,
			String fallback) {
		String first = value.apply(geometries.get(0));
		return geometries.stream().allMatch(geometry -> first.equals(value.apply(geometry))) ? first : fallback;
	}

	private static GeometryWrapper inSrs(GeometryWrapper geometry, String srsUri) {
		if (srsUri.equals(geometry.getSrsURI())) {
			return geometry;
		}
		try {
			return geometry.transform(srsUri);
		} catch (FactoryException | TransformException | RuntimeException e) {
			throw new JenaGeoSparqlException("Cannot transform " + geometry.getSrsURI() + " to " + srsUri
					+ " for a spatial aggregate", e);
		}
	}
}
