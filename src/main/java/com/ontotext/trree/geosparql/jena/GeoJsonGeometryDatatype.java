package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.geosparql.implementation.DimensionInfo;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.GeometryDatatype;
import org.apache.jena.geosparql.implementation.jts.CoordinateSequenceDimensions;
import org.apache.jena.geosparql.implementation.jts.CustomCoordinateSequence;
import org.apache.jena.geosparql.implementation.jts.CustomGeometryFactory;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.json.simple.parser.JSONParser;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Strict GeoJSON geometry datatype with CRS84 longitude/latitude semantics.
 */
final class GeoJsonGeometryDatatype extends GeometryDatatype {
	static final GeoJsonGeometryDatatype INSTANCE = new GeoJsonGeometryDatatype();

	private static final GeometryFactory GEOMETRY_FACTORY = CustomGeometryFactory.theInstance();

	private GeoJsonGeometryDatatype() {
		super(GeoConstants.GEO_JSON_LITERAL.stringValue());
	}

	@Override
	public GeometryWrapper read(String geometryLiteral) {
		try {
			ParsedGeometry validated = geometryLiteral.isEmpty()
					? new ParsedGeometry(GEOMETRY_FACTORY.createPoint(emptySequence()), 2)
					: parseGeometry(rootObject(new JSONParser().parse(geometryLiteral)));
			int coordinateDimension = validated.coordinateDimension() == 0 ? 2 : validated.coordinateDimension();
			Geometry geometry = geometryLiteral.isEmpty()
					? validated.geometry()
					: normalizeCoordinateSequences(
							new GeoJsonReader(GEOMETRY_FACTORY).read(geometryLiteral), coordinateDimension);
			return new GeometryWrapper(geometry, SRS_URI.DEFAULT_WKT_CRS84, getURI(),
					new DimensionInfo(coordinateDimension, coordinateDimension,
							geometry.getDimension()), geometryLiteral);
		} catch (DatatypeFormatException e) {
			throw e;
		} catch (Exception e) {
			throw invalid(e.getMessage(), e);
		}
	}

	@Override
	public String unparse(Object value) {
		if (!(value instanceof GeometryWrapper wrapper)) {
			throw new DatatypeFormatException("Object to unparse is not a GeometryWrapper: " + value);
		}
		GeoJsonWriter writer = new GeoJsonWriter();
		writer.setEncodeCRS(false);
		writer.setForceCCW(true);
		return writer.write(wrapper.getParsingGeometry());
	}

	private static ParsedGeometry parseGeometry(Map<?, ?> object) {
		if (object.containsKey("crs")) {
			throw invalid("the legacy crs member is not permitted", null);
		}
		Object typeValue = object.get("type");
		if (!(typeValue instanceof String type)) {
			throw invalid("type must be a supported Geometry type", null);
		}
		if ("GeometryCollection".equals(type)) {
			if (object.containsKey("coordinates")) {
				throw invalid("GeometryCollection must use geometries instead of coordinates", null);
			}
		} else if (object.containsKey("geometries")) {
			throw invalid(type + " must use coordinates instead of geometries", null);
		}

		ParsedGeometry parsed;
		switch (type) {
			case "Point":
				parsed = parsePoint(object);
				break;
			case "MultiPoint":
				parsed = parseMultiPoint(object);
				break;
			case "LineString":
				parsed = parseLineString(object);
				break;
			case "MultiLineString":
				parsed = parseMultiLineString(object);
				break;
			case "Polygon":
				parsed = parsePolygon(object);
				break;
			case "MultiPolygon":
				parsed = parseMultiPolygon(object);
				break;
			case "GeometryCollection":
				parsed = parseGeometryCollection(object);
				break;
			default:
				throw invalid("unsupported type: " + type, null);
		}
		validateBbox(object, parsed.coordinateDimension() == 0 ? 2 : parsed.coordinateDimension());
		return parsed;
	}

	private static ParsedGeometry parsePoint(Map<?, ?> object) {
		List<?> position = requiredArray(object, "coordinates", "Point coordinates");
		if (position.isEmpty()) {
			return new ParsedGeometry(GEOMETRY_FACTORY.createPoint(emptySequence()), 0);
		}
		DimensionTracker dimensions = new DimensionTracker();
		CustomCoordinateSequence sequence = positionSequence(position, dimensions);
		return new ParsedGeometry(GEOMETRY_FACTORY.createPoint(sequence), dimensions.value());
	}

	private static ParsedGeometry parseMultiPoint(Map<?, ?> object) {
		List<?> positions = requiredArray(object, "coordinates", "MultiPoint coordinates");
		DimensionTracker dimensions = new DimensionTracker();
		CustomCoordinateSequence sequence = positionsSequence(positions, dimensions, 0, true);
		return new ParsedGeometry(GEOMETRY_FACTORY.createMultiPoint(sequence), dimensions.value());
	}

	private static ParsedGeometry parseLineString(Map<?, ?> object) {
		List<?> positions = requiredArray(object, "coordinates", "LineString coordinates");
		DimensionTracker dimensions = new DimensionTracker();
		CustomCoordinateSequence sequence = positionsSequence(positions, dimensions, 2, true);
		return new ParsedGeometry(GEOMETRY_FACTORY.createLineString(sequence), dimensions.value());
	}

	private static ParsedGeometry parseMultiLineString(Map<?, ?> object) {
		List<?> lines = requiredArray(object, "coordinates", "MultiLineString coordinates");
		DimensionTracker dimensions = new DimensionTracker();
		List<LineString> lineStrings = new ArrayList<>();
		for (Object value : lines) {
			List<?> positions = array(value, "MultiLineString member");
			lineStrings.add(GEOMETRY_FACTORY.createLineString(
					positionsSequence(positions, dimensions, 2, false)));
		}
		return new ParsedGeometry(GEOMETRY_FACTORY.createMultiLineString(
				lineStrings.toArray(new LineString[0])), dimensions.value());
	}

	private static ParsedGeometry parsePolygon(Map<?, ?> object) {
		List<?> rings = requiredArray(object, "coordinates", "Polygon coordinates");
		DimensionTracker dimensions = new DimensionTracker();
		Polygon polygon = polygon(rings, dimensions, true);
		return new ParsedGeometry(polygon, dimensions.value());
	}

	private static ParsedGeometry parseMultiPolygon(Map<?, ?> object) {
		List<?> polygons = requiredArray(object, "coordinates", "MultiPolygon coordinates");
		DimensionTracker dimensions = new DimensionTracker();
		List<Polygon> parsedPolygons = new ArrayList<>();
		for (Object value : polygons) {
			parsedPolygons.add(polygon(array(value, "MultiPolygon member"), dimensions, false));
		}
		return new ParsedGeometry(GEOMETRY_FACTORY.createMultiPolygon(
				parsedPolygons.toArray(new Polygon[0])), dimensions.value());
	}

	private static ParsedGeometry parseGeometryCollection(Map<?, ?> object) {
		List<?> geometries = requiredArray(object, "geometries", "GeometryCollection geometries");
		List<Geometry> parsedGeometries = new ArrayList<>();
		int coordinateDimension = 0;
		for (Object value : geometries) {
			ParsedGeometry member = parseGeometry(rootObject(value));
			coordinateDimension = mergeDimensions(coordinateDimension, member.coordinateDimension());
			parsedGeometries.add(member.geometry());
		}
		return new ParsedGeometry(GEOMETRY_FACTORY.createGeometryCollection(
				parsedGeometries.toArray(new Geometry[0])), coordinateDimension);
	}

	private static Polygon polygon(List<?> rings, DimensionTracker dimensions, boolean allowEmpty) {
		if (rings.isEmpty()) {
			if (!allowEmpty) {
				throw invalid("MultiPolygon members cannot have empty coordinate arrays", null);
			}
			return GEOMETRY_FACTORY.createPolygon();
		}
		List<LinearRing> parsedRings = new ArrayList<>();
		for (Object value : rings) {
			List<?> positions = array(value, "Polygon ring");
			CustomCoordinateSequence sequence = positionsSequence(positions, dimensions, 4, false);
			if (!samePosition(positions.get(0), positions.get(positions.size() - 1))) {
				throw invalid("Polygon rings must be closed", null);
			}
			parsedRings.add(GEOMETRY_FACTORY.createLinearRing(sequence));
		}
		LinearRing shell = parsedRings.get(0);
		LinearRing[] holes = parsedRings.subList(1, parsedRings.size()).toArray(new LinearRing[0]);
		return GEOMETRY_FACTORY.createPolygon(shell, holes);
	}

	private static CustomCoordinateSequence positionsSequence(List<?> positions, DimensionTracker dimensions,
			int minimumSize, boolean allowEmpty) {
		if (positions.isEmpty()) {
			if (!allowEmpty) {
				throw invalid("coordinate array must not be empty", null);
			}
			return emptySequence();
		}
		if (positions.size() < minimumSize) {
			throw invalid("coordinate array has too few positions", null);
		}
		List<List<?>> parsedPositions = new ArrayList<>();
		for (Object value : positions) {
			List<?> position = array(value, "position");
			validatePosition(position, dimensions);
			parsedPositions.add(position);
		}
		CustomCoordinateSequence sequence = new CustomCoordinateSequence(parsedPositions.size(),
				dimensions.sequenceDimensions());
		for (int index = 0; index < parsedPositions.size(); index++) {
			setPosition(sequence, index, parsedPositions.get(index));
		}
		return sequence;
	}

	private static CustomCoordinateSequence positionSequence(List<?> position, DimensionTracker dimensions) {
		validatePosition(position, dimensions);
		CustomCoordinateSequence sequence = new CustomCoordinateSequence(1, dimensions.sequenceDimensions());
		setPosition(sequence, 0, position);
		return sequence;
	}

	private static void validatePosition(List<?> position, DimensionTracker dimensions) {
		if (position.size() != 2 && position.size() != 3) {
			throw invalid("positions must contain two or three ordinates", null);
		}
		dimensions.accept(position.size());
		for (int ordinate = 0; ordinate < position.size(); ordinate++) {
			double number = finiteNumber(position.get(ordinate), "position ordinates");
			if (ordinate == 0 && (number < -180.0 || number > 180.0)) {
				throw invalid("longitude is outside the CRS84 domain", null);
			}
			if (ordinate == 1 && (number < -90.0 || number > 90.0)) {
				throw invalid("latitude is outside the CRS84 domain", null);
			}
		}
	}

	private static void setPosition(CustomCoordinateSequence sequence, int index, List<?> position) {
		for (int ordinate = 0; ordinate < position.size(); ordinate++) {
			sequence.setOrdinate(index, ordinate, ((Number) position.get(ordinate)).doubleValue());
		}
	}

	private static boolean samePosition(Object firstValue, Object lastValue) {
		List<?> first = array(firstValue, "first Polygon ring position");
		List<?> last = array(lastValue, "last Polygon ring position");
		if (first.size() != last.size()) {
			return false;
		}
		for (int index = 0; index < first.size(); index++) {
			if (((Number) first.get(index)).doubleValue() != ((Number) last.get(index)).doubleValue()) {
				return false;
			}
		}
		return true;
	}

	private static void validateBbox(Map<?, ?> object, int coordinateDimension) {
		if (!object.containsKey("bbox")) {
			return;
		}
		List<?> bbox = array(object.get("bbox"), "bbox");
		if (bbox.size() != coordinateDimension * 2) {
			throw invalid("bbox dimension does not match the geometry", null);
		}
		double[] values = new double[bbox.size()];
		for (int index = 0; index < bbox.size(); index++) {
			values[index] = finiteNumber(bbox.get(index), "bbox ordinates");
			int axis = index % coordinateDimension;
			if (axis == 0 && (values[index] < -180.0 || values[index] > 180.0)) {
				throw invalid("bbox longitude is outside the CRS84 domain", null);
			}
			if (axis == 1 && (values[index] < -90.0 || values[index] > 90.0)) {
				throw invalid("bbox latitude is outside the CRS84 domain", null);
			}
		}
		for (int axis = 1; axis < coordinateDimension; axis++) {
			if (values[axis] > values[axis + coordinateDimension]) {
				throw invalid("bbox lower ordinate exceeds its upper ordinate", null);
			}
		}
	}

	private static Map<?, ?> rootObject(Object value) {
		if (!(value instanceof Map<?, ?> object)) {
			throw invalid("root must be an object", null);
		}
		return object;
	}

	private static List<?> requiredArray(Map<?, ?> object, String member, String description) {
		if (!object.containsKey(member)) {
			throw invalid(description + " are required", null);
		}
		return array(object.get(member), description);
	}

	private static List<?> array(Object value, String description) {
		if (!(value instanceof List<?> list)) {
			throw invalid(description + " must be an array", null);
		}
		return list;
	}

	private static double finiteNumber(Object value, String description) {
		if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
			throw invalid(description + " must be finite numbers", null);
		}
		return number.doubleValue();
	}

	private static CustomCoordinateSequence emptySequence() {
		return new CustomCoordinateSequence(0, CoordinateSequenceDimensions.XY);
	}

	static Geometry normalizeCoordinateSequences(Geometry geometry, int coordinateDimension) {
		if (geometry instanceof Point point) {
			return GEOMETRY_FACTORY.createPoint(copySequence(point.getCoordinateSequence(), coordinateDimension));
		}
		if (geometry instanceof LinearRing ring) {
			return GEOMETRY_FACTORY.createLinearRing(copySequence(ring.getCoordinateSequence(), coordinateDimension));
		}
		if (geometry instanceof LineString lineString) {
			return GEOMETRY_FACTORY.createLineString(
					copySequence(lineString.getCoordinateSequence(), coordinateDimension));
		}
		if (geometry instanceof Polygon polygon) {
			if (polygon.isEmpty()) {
				return GEOMETRY_FACTORY.createPolygon();
			}
			LinearRing shell = (LinearRing) normalizeCoordinateSequences(
					polygon.getExteriorRing(), coordinateDimension);
			LinearRing[] holes = new LinearRing[polygon.getNumInteriorRing()];
			for (int index = 0; index < holes.length; index++) {
				holes[index] = (LinearRing) normalizeCoordinateSequences(
						polygon.getInteriorRingN(index), coordinateDimension);
			}
			return GEOMETRY_FACTORY.createPolygon(shell, holes);
		}
		if (geometry instanceof MultiPoint multiPoint) {
			Point[] points = new Point[multiPoint.getNumGeometries()];
			for (int index = 0; index < points.length; index++) {
				points[index] = (Point) normalizeCoordinateSequences(
						multiPoint.getGeometryN(index), coordinateDimension);
			}
			return GEOMETRY_FACTORY.createMultiPoint(points);
		}
		if (geometry instanceof MultiLineString multiLineString) {
			LineString[] lines = new LineString[multiLineString.getNumGeometries()];
			for (int index = 0; index < lines.length; index++) {
				lines[index] = (LineString) normalizeCoordinateSequences(
						multiLineString.getGeometryN(index), coordinateDimension);
			}
			return GEOMETRY_FACTORY.createMultiLineString(lines);
		}
		if (geometry instanceof MultiPolygon multiPolygon) {
			Polygon[] polygons = new Polygon[multiPolygon.getNumGeometries()];
			for (int index = 0; index < polygons.length; index++) {
				polygons[index] = (Polygon) normalizeCoordinateSequences(
						multiPolygon.getGeometryN(index), coordinateDimension);
			}
			return GEOMETRY_FACTORY.createMultiPolygon(polygons);
		}
		if (geometry instanceof GeometryCollection collection) {
			Geometry[] geometries = new Geometry[collection.getNumGeometries()];
			for (int index = 0; index < geometries.length; index++) {
				geometries[index] = normalizeCoordinateSequences(
						collection.getGeometryN(index), coordinateDimension);
			}
			return GEOMETRY_FACTORY.createGeometryCollection(geometries);
		}
		throw new DatatypeFormatException("Unsupported GeoJSON geometry type: " + geometry.getGeometryType());
	}

	private static CustomCoordinateSequence copySequence(CoordinateSequence source, int coordinateDimension) {
		CoordinateSequenceDimensions dimensions = coordinateDimension == 3
				? CoordinateSequenceDimensions.XYZ
				: CoordinateSequenceDimensions.XY;
		CustomCoordinateSequence target = new CustomCoordinateSequence(source.size(), dimensions);
		for (int index = 0; index < source.size(); index++) {
			target.setOrdinate(index, 0, source.getX(index));
			target.setOrdinate(index, 1, source.getY(index));
			if (coordinateDimension == 3) {
				target.setOrdinate(index, 2, source.getZ(index));
			}
		}
		return target;
	}

	private static int mergeDimensions(int current, int next) {
		if (current != 0 && next != 0 && current != next) {
			throw invalid("geometry coordinates must use one homogeneous dimension", null);
		}
		return current == 0 ? next : current;
	}

	private static DatatypeFormatException invalid(String detail, Throwable cause) {
		String message = "Invalid GeoJSON geometry literal: " + detail;
		return cause == null ? new DatatypeFormatException(message) : new DatatypeFormatException(message, cause);
	}

	private record ParsedGeometry(Geometry geometry, int coordinateDimension) {
	}

	private static final class DimensionTracker {
		private int value;

		void accept(int dimension) {
			value = mergeDimensions(value, dimension);
		}

		int value() {
			return value;
		}

		CoordinateSequenceDimensions sequenceDimensions() {
			return value == 3 ? CoordinateSequenceDimensions.XYZ : CoordinateSequenceDimensions.XY;
		}
	}
}
