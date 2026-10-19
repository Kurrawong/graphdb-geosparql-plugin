package com.ontotext.trree.geosparql.jena.query;

import com.ontotext.trree.geosparql.jena.JenaGeoSparqlException;
import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.GeometryWrapperFactory;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.jts.CustomGeometryFactory;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.locationtech.jts.geom.Geometry;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

import java.util.List;
import java.util.function.Function;

/**
 * Calculates GeoSPARQL spatial aggregates over the geometries of one group.
 */
public final class GeometryAggregate {
	private GeometryAggregate() {
	}

	/**
	 * Collects the geometries into one GeometryCollection in a shared SRS and geometry datatype.
	 */
	public static GeometryWrapper collect(List<GeometryWrapper> geometries) {
		String srsUri = shared(geometries, GeometryWrapper::getSrsURI, SRS_URI.DEFAULT_WKT_CRS84);
		String datatypeUri = shared(geometries, GeometryWrapper::getGeometryDatatypeURI, WKTDatatype.URI);
		Geometry[] members = geometries.stream()
				.map(geometry -> inSrs(geometry, srsUri).getXYGeometry())
				.toArray(Geometry[]::new);
		return wrap(CustomGeometryFactory.theInstance().createGeometryCollection(members), srsUri, datatypeUri);
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
	 * Calculates the union of the collected geometries.
	 */
	public static GeometryWrapper union(List<GeometryWrapper> geometries) {
		GeometryWrapper collected = collect(geometries);
		return wrap(collected.getXYGeometry().union(), collected.getSrsURI(), collected.getGeometryDatatypeURI());
	}

	private static GeometryWrapper wrap(Geometry xyGeometry, String srsUri, String datatypeUri) {
		if (xyGeometry.isEmpty()) {
			return new GeometryWrapper(xyGeometry, srsUri, datatypeUri,
					new DimensionInfo(2, 2, xyGeometry.getDimension()));
		}
		return GeometryWrapperFactory.createGeometry(xyGeometry, srsUri, datatypeUri);
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
