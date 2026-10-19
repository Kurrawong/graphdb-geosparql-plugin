package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.GeometryDatatype;
import org.apache.jena.geosparql.implementation.jts.CustomGeometryFactory;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.kml.KMLReader;
import org.locationtech.jts.io.kml.KMLWriter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KML geometry datatype with CRS84 longitude/latitude semantics, parsed by the JTS KML reader.
 */
final class KmlGeometryDatatype extends GeometryDatatype {
	static final KmlGeometryDatatype INSTANCE = new KmlGeometryDatatype();

	private static final GeometryFactory GEOMETRY_FACTORY = CustomGeometryFactory.theInstance();
	private static final String KML_NAMESPACE = "http://www.opengis.net/kml/2.2";
	private static final Pattern COORDINATES = Pattern.compile("<coordinates>([^<]*)</coordinates>");
	private static final Pattern ORDINATE = Pattern.compile("[^,\\s]+");

	private KmlGeometryDatatype() {
		super(GeoConstants.GEO_KML_LITERAL.stringValue());
	}

	@Override
	public GeometryWrapper read(String geometryLiteral) {
		Geometry parsed;
		if (geometryLiteral.isEmpty()) {
			parsed = GEOMETRY_FACTORY.createPoint();
		} else if (geometryLiteral.contains("<!DOCTYPE")) {
			// The JTS reader uses a default XMLInputFactory, which resolves DTD entities.
			throw new DatatypeFormatException("Invalid KML geometry literal: document type declarations are not permitted");
		} else {
			try {
				parsed = new KMLReader(GEOMETRY_FACTORY).read(geometryLiteral);
			} catch (Exception e) {
				throw new DatatypeFormatException("Invalid KML geometry literal: " + e.getMessage(), e);
			}
			if (parsed == null) {
				// The JTS reader returns no geometry for an empty MultiGeometry.
				parsed = GEOMETRY_FACTORY.createGeometryCollection();
			}
		}
		requireFiniteOrdinates(parsed);
		int coordinateDimension = everyCoordinateHasAltitude(parsed) ? 3 : 2;
		Geometry geometry = GeoJsonGeometryDatatype.normalizeCoordinateSequences(parsed, coordinateDimension);
		return new GeometryWrapper(geometry, SRS_URI.DEFAULT_WKT_CRS84, getURI(),
				new DimensionInfo(coordinateDimension, coordinateDimension, geometry.getDimension()),
				geometryLiteral);
	}

	/**
	 * Rejects infinite ordinates, including altitudes that dimension normalization would discard and that the
	 * KML writer cannot represent. A NaN altitude denotes an absent altitude.
	 */
	private static void requireFiniteOrdinates(Geometry geometry) {
		for (Coordinate coordinate : geometry.getCoordinates()) {
			if (Double.isInfinite(coordinate.getX()) || Double.isInfinite(coordinate.getY())
					|| Double.isInfinite(coordinate.getZ())) {
				throw new DatatypeFormatException("Invalid KML geometry literal: ordinates must be finite");
			}
		}
	}

	private static boolean everyCoordinateHasAltitude(Geometry geometry) {
		if (geometry.isEmpty()) {
			return false;
		}
		for (Coordinate coordinate : geometry.getCoordinates()) {
			if (Double.isNaN(coordinate.getZ())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String unparse(Object value) {
		if (!(value instanceof GeometryWrapper wrapper)) {
			throw new DatatypeFormatException("Object to unparse is not a GeometryWrapper: " + value);
		}
		if (wrapper.isEmpty()) {
			return "";
		}
		Geometry geometry = flattenedWithoutEmptyMembers(wrapper.getParsingGeometry());
		requireFiniteOrdinates(geometry);
		String kml = plainOrdinates(new KMLWriter().write(geometry));
		int rootNameEnd = kml.indexOf('>');
		return kml.substring(0, rootNameEnd) + " xmlns=\"" + KML_NAMESPACE + "\"" + kml.substring(rootNameEnd);
	}

	/**
	 * Rewrites every ordinate as a plain decimal. Without a precision setting, the JTS writer formats ordinates
	 * with {@link Double#toString(double)}, which is locale-independent but may use exponent notation; the writer's
	 * precision setting uses the default locale's digits and signs instead.
	 */
	private static String plainOrdinates(String kml) {
		Matcher coordinates = COORDINATES.matcher(kml);
		StringBuilder result = new StringBuilder();
		while (coordinates.find()) {
			String plain = ORDINATE.matcher(coordinates.group(1)).replaceAll(ordinate ->
					new BigDecimal(ordinate.group()).stripTrailingZeros().toPlainString());
			coordinates.appendReplacement(result, Matcher.quoteReplacement("<coordinates>" + plain + "</coordinates>"));
		}
		coordinates.appendTail(result);
		return result.toString();
	}

	/**
	 * Flattens nested collections and removes empty members. The JTS reader cannot read a nested
	 * {@code MultiGeometry} and KML cannot represent an empty geometry. Every collection is written as a
	 * {@code MultiGeometry}, so the rebuilt collection type does not affect the output.
	 */
	private static Geometry flattenedWithoutEmptyMembers(Geometry geometry) {
		if (!(geometry instanceof GeometryCollection)) {
			return geometry;
		}
		List<Geometry> members = new ArrayList<>();
		addNonEmptyLeafMembers(geometry, members);
		return GEOMETRY_FACTORY.createGeometryCollection(members.toArray(new Geometry[0]));
	}

	private static void addNonEmptyLeafMembers(Geometry geometry, List<Geometry> members) {
		if (!(geometry instanceof GeometryCollection collection)) {
			if (!geometry.isEmpty()) {
				members.add(geometry);
			}
			return;
		}
		for (int index = 0; index < collection.getNumGeometries(); index++) {
			addNonEmptyLeafMembers(collection.getGeometryN(index), members);
		}
	}
}
