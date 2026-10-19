package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.Crs84ResultDimensionPolicy;
import com.ontotext.trree.geosparql.jena.query.GeometryArea;
import com.ontotext.trree.geosparql.jena.query.GeometryBoundingCircle;
import com.ontotext.trree.geosparql.jena.query.GeometryCentroid;
import com.ontotext.trree.geosparql.jena.query.GeometryConcaveHull;
import com.ontotext.trree.geosparql.jena.query.GeometryCount;
import com.ontotext.trree.geosparql.jena.query.GeometryCoordinateExtrema;
import com.ontotext.trree.geosparql.jena.query.DirectionalPointCoordinates;
import com.ontotext.trree.geosparql.jena.query.GeometryDistance;
import com.ontotext.trree.geosparql.jena.query.GeometryLength;
import com.ontotext.trree.geosparql.jena.query.GeometryMember;
import com.ontotext.trree.geosparql.jena.query.GeometryMetadata;
import com.ontotext.trree.geosparql.jena.query.GeometrySimplify;
import com.ontotext.trree.geosparql.jena.query.MetricBuffer;
import com.ontotext.trree.geosparql.jena.query.MetricWithinDistance;
import com.ontotext.trree.geosparql.jena.query.PointCoordinates;
import com.ontotext.trree.geosparql.jena.query.TopologicalDimension;
import com.ontotext.trree.geosparql.jena.query.WithinDistance;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

final class QueryFunctionManifest {
	private static final List<Entry> ENTRIES = List.of(
			new Entry(GeoConstants.GEOF_AREA.stringValue(), 2,
					new UnaryGeometryUnitToDoubleProvider(GeometryArea::calculate, 2)),
			new Entry(GeoConstants.GEOF_BOUNDARY.stringValue(), 1,
					new UnaryGeometryProvider(GeometryWrapper::boundary,
							Crs84ResultDimensionPolicy.PRESERVE_DEFINED_Z)),
			new Entry(GeoConstants.GEOF_BOUNDING_CIRCLE.stringValue(), 1,
					new UnaryGeometryProvider(GeometryBoundingCircle::calculate,
							Crs84ResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_BUFFER.stringValue(), 3,
					new UnaryGeometryDoubleUnitToGeometryProvider(GeometryWrapper::buffer,
							Crs84ResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_CENTROID.stringValue(), 1,
					new UnaryGeometryProvider(GeometryCentroid::calculate,
							Crs84ResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_CONCAVE_HULL.stringValue(), 1,
					new UnaryGeometryProvider(GeometryConcaveHull::calculate,
							Crs84ResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_CONVEX_HULL.stringValue(), 1,
					new UnaryGeometryProvider(GeometryWrapper::convexHull,
							Crs84ResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_DIFFERENCE.stringValue(), 2,
					new BinaryGeometryProvider(GeometryWrapper::difference,
							Crs84ResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_DISTANCE.stringValue(), 3,
					new BinaryGeometryUnitToDoubleProvider(GeometryDistance::calculate)),
			new Entry(GeoConstants.GEOF_ENVELOPE.stringValue(), 1,
					new UnaryGeometryProvider(GeometryWrapper::envelope,
							Crs84ResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_INTERSECTION.stringValue(), 2,
					new BinaryGeometryProvider(GeometryWrapper::intersection,
							Crs84ResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_LENGTH.stringValue(), 2,
					new UnaryGeometryUnitToDoubleProvider(GeometryLength::calculate)),
			new Entry(GeoConstants.GEOF_METRIC_AREA.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(GeometryArea::calculateMetric)),
			new Entry(GeoConstants.GEOF_METRIC_BUFFER.stringValue(), 2,
					new UnaryGeometryDoubleToGeometryProvider(MetricBuffer::calculate,
							Crs84ResultDimensionPolicy.XY_ONLY, "radius")),
			new Entry(GeoConstants.GEOF_METRIC_DISTANCE.stringValue(), 2,
					new BinaryGeometryToDoubleProvider(GeometryDistance::calculateMetric)),
			new Entry(GeoConstants.GEOF_METRIC_WITHIN_DISTANCE.stringValue(), 3,
					new BinaryGeometryDoubleToBooleanProvider(MetricWithinDistance::calculate)),
			new Entry(GeoConstants.GEOF_WITHIN_DISTANCE.stringValue(), 4,
					new BinaryGeometryDoubleUnitToBooleanProvider(WithinDistance::calculate)),
			new Entry(GeoConstants.GEOF_METRIC_LENGTH.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(GeometryLength::calculateMetric)),
			new Entry(GeoConstants.GEOF_METRIC_PERIMETER.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(GeometryLength::calculateMetric)),
			new Entry(GeoConstants.GEOF_PERIMETER.stringValue(), 2,
					new UnaryGeometryUnitToDoubleProvider(GeometryLength::calculate)),
			new Entry(GeoConstants.GEOF_MAX_X.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(GeometryCoordinateExtrema::maxX)),
			new Entry(GeoConstants.GEOF_MAX_Y.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(GeometryCoordinateExtrema::maxY)),
			new Entry(GeoConstants.GEOF_MAX_Z.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(GeometryCoordinateExtrema::maxZ)),
			new Entry(GeoConstants.GEOF_MIN_X.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(GeometryCoordinateExtrema::minX)),
			new Entry(GeoConstants.GEOF_MIN_Y.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(GeometryCoordinateExtrema::minY)),
			new Entry(GeoConstants.GEOF_MIN_Z.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(GeometryCoordinateExtrema::minZ)),
			new Entry(GeoConstants.GEOF_X.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(PointCoordinates::x)),
			new Entry(GeoConstants.GEOF_Y.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(PointCoordinates::y)),
			new Entry(GeoConstants.GEOF_Z.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(PointCoordinates::z)),
			new Entry(GeoConstants.GEOF_M.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(PointCoordinates::m)),
			new Entry(GeoConstants.GEOF_EASTING.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(DirectionalPointCoordinates::easting)),
			new Entry(GeoConstants.GEOF_NORTHING.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(DirectionalPointCoordinates::northing)),
			new Entry(GeoConstants.GEOF_COORDINATE_DIMENSION.stringValue(), 1,
					new UnaryGeometryIntegerProvider(GeometryWrapper::getCoordinateDimension)),
			new Entry(GeoConstants.GEOF_DIMENSION.stringValue(), 1,
					new UnaryGeometryIntegerProvider(TopologicalDimension::calculate)),
			new Entry(GeoConstants.GEOF_GEOMETRY_N.stringValue(), 2,
					new GeometryMemberProvider(GeometryMember::calculate,
							Crs84ResultDimensionPolicy.PRESERVE_DEFINED_Z)),
			new Entry(GeoConstants.GEOF_GEOMETRY_TYPE.stringValue(), 1,
					new UnaryGeometryAnyUriProvider(GeometryMetadata::geometryTypeUri)),
			new Entry(GeoConstants.GEOF_IS_3D.stringValue(), 1,
					new UnaryGeometryBooleanProvider(GeometryMetadata::is3D)),
			new Entry(GeoConstants.GEOF_IS_EMPTY.stringValue(), 1,
					new UnaryGeometryBooleanProvider(GeometryWrapper::isEmpty)),
			new Entry(GeoConstants.GEOF_IS_MEASURED.stringValue(), 1,
					new UnaryGeometryBooleanProvider(GeometryMetadata::isMeasured)),
			new Entry(GeoConstants.GEOF_IS_SIMPLE.stringValue(), 1,
					new UnaryGeometryBooleanProvider(GeometryWrapper::isSimple)),
			new Entry(GeoConstants.GEOF_NUM_GEOMETRIES.stringValue(), 1,
					new UnaryGeometryIntegerProvider(GeometryCount::calculate)),
			new Entry(GeoConstants.GEOF_SPATIAL_DIMENSION.stringValue(), 1,
					new UnaryGeometryIntegerProvider(GeometryWrapper::getSpatialDimension)),
			new Entry(GeoConstants.GEOF_SYM_DIFFERENCE.stringValue(), 2,
					new BinaryGeometryProvider(GeometryWrapper::symDifference,
							Crs84ResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_SIMPLIFY.stringValue(), 2,
					new UnaryGeometryDoubleToGeometryProvider(GeometrySimplify::calculate,
							Crs84ResultDimensionPolicy.PRESERVE_DEFINED_Z, "tolerance")),
			new Entry(GeoConstants.GEOF_TRANSFORM.stringValue(), 2,
					new GeometryTargetSrsProvider(GeometryWrapper::transform,
							Crs84ResultDimensionPolicy.PRESERVE_DEFINED_Z)),
			new Entry(GeoConstants.GEOF_TRANSFORM_CRS84.stringValue(), 1,
					new GeometryFixedTargetSrsProvider(GeometryWrapper::transform,
							SRS_URI.DEFAULT_WKT_CRS84,
							Crs84ResultDimensionPolicy.PRESERVE_DEFINED_Z)),
			new Entry(GeoConstants.GEOF_UNION.stringValue(), 2,
					new BinaryGeometryProvider(GeometryWrapper::union,
							Crs84ResultDimensionPolicy.XY_ONLY)));

	private QueryFunctionManifest() {
	}

	static List<Entry> entries() {
		return ENTRIES;
	}

	record Entry(String uri, int mandatoryArity, Provider provider) {
	}

	sealed interface Provider permits BinaryGeometryProvider, BinaryGeometryToDoubleProvider,
			BinaryGeometryDoubleToBooleanProvider,
			BinaryGeometryDoubleUnitToBooleanProvider,
			BinaryGeometryUnitToDoubleProvider,
			GeometryFixedTargetSrsProvider, GeometryMemberProvider, GeometryTargetSrsProvider,
			UnaryGeometryAnyUriProvider,
			UnaryGeometryBooleanProvider,
			UnaryGeometryDoubleToGeometryProvider, UnaryGeometryDoubleUnitToGeometryProvider,
			UnaryGeometryIntegerProvider, UnaryGeometryProvider, UnaryGeometryToDoubleProvider,
			UnaryGeometryUnitToDoubleProvider {
	}

	@FunctionalInterface
	interface BinaryGeometryCalculation {
		GeometryWrapper apply(GeometryWrapper left, GeometryWrapper right) throws Exception;
	}

	record BinaryGeometryProvider(BinaryGeometryCalculation calculation,
			Crs84ResultDimensionPolicy crs84ResultDimensionPolicy) implements Provider {
	}

	@FunctionalInterface
	interface BinaryGeometryToDoubleCalculation {
		double apply(GeometryWrapper left, GeometryWrapper right) throws Exception;
	}

	record BinaryGeometryToDoubleProvider(BinaryGeometryToDoubleCalculation calculation) implements Provider {
	}

	@FunctionalInterface
	interface BinaryGeometryDoubleToBooleanCalculation {
		boolean apply(GeometryWrapper left, GeometryWrapper right, double distance) throws Exception;
	}

	record BinaryGeometryDoubleToBooleanProvider(BinaryGeometryDoubleToBooleanCalculation calculation)
			implements Provider {
	}

	@FunctionalInterface
	interface BinaryGeometryDoubleUnitToBooleanCalculation {
		boolean apply(GeometryWrapper left, GeometryWrapper right, double distance, String unitUri)
				throws Exception;
	}

	record BinaryGeometryDoubleUnitToBooleanProvider(BinaryGeometryDoubleUnitToBooleanCalculation calculation)
			implements Provider {
	}

	@FunctionalInterface
	interface BinaryGeometryUnitToDoubleCalculation {
		double apply(GeometryWrapper left, GeometryWrapper right, String unitUri) throws Exception;
	}

	record BinaryGeometryUnitToDoubleProvider(BinaryGeometryUnitToDoubleCalculation calculation) implements Provider {
	}

	@FunctionalInterface
	interface UnaryGeometryDoubleToGeometryCalculation {
		GeometryWrapper apply(GeometryWrapper geometry, double value) throws Exception;
	}

	record UnaryGeometryDoubleToGeometryProvider(UnaryGeometryDoubleToGeometryCalculation calculation,
			Crs84ResultDimensionPolicy crs84ResultDimensionPolicy, String numericRole)
			implements Provider {
	}

	@FunctionalInterface
	interface UnaryGeometryDoubleUnitToGeometryCalculation {
		GeometryWrapper apply(GeometryWrapper geometry, double value, String unitUri) throws Exception;
	}

	record UnaryGeometryDoubleUnitToGeometryProvider(UnaryGeometryDoubleUnitToGeometryCalculation calculation,
			Crs84ResultDimensionPolicy crs84ResultDimensionPolicy)
			implements Provider {
	}

	@FunctionalInterface
	interface UnaryGeometryCalculation {
		GeometryWrapper apply(GeometryWrapper geometry) throws Exception;
	}

	record UnaryGeometryProvider(UnaryGeometryCalculation calculation,
			Crs84ResultDimensionPolicy crs84ResultDimensionPolicy) implements Provider {
	}

	@FunctionalInterface
	interface UnaryGeometryUnitToDoubleCalculation {
		double apply(GeometryWrapper geometry, String unitUri) throws Exception;
	}

	record UnaryGeometryUnitToDoubleProvider(UnaryGeometryUnitToDoubleCalculation calculation,
			int unitPower) implements Provider {
		UnaryGeometryUnitToDoubleProvider(UnaryGeometryUnitToDoubleCalculation calculation) {
			this(calculation, 1);
		}

		UnaryGeometryUnitToDoubleProvider {
			if (unitPower != 1 && unitPower != 2) {
				throw new IllegalArgumentException("Measurement output must use linear or squared units.");
			}
		}
	}

	record GeometryMemberProvider(BiFunction<GeometryWrapper, Integer, GeometryWrapper> calculation,
			Crs84ResultDimensionPolicy crs84ResultDimensionPolicy)
			implements Provider {
	}

	@FunctionalInterface
	interface GeometryTargetSrsCalculation {
		GeometryWrapper apply(GeometryWrapper geometry, String targetSrsUri) throws Exception;
	}

	record GeometryTargetSrsProvider(GeometryTargetSrsCalculation calculation,
			Crs84ResultDimensionPolicy crs84ResultDimensionPolicy) implements Provider {
	}

	record GeometryFixedTargetSrsProvider(GeometryTargetSrsCalculation calculation,
			String targetSrsUri, Crs84ResultDimensionPolicy crs84ResultDimensionPolicy)
			implements Provider {
	}

	record UnaryGeometryAnyUriProvider(Function<GeometryWrapper, String> calculation) implements Provider {
	}

	record UnaryGeometryBooleanProvider(Predicate<GeometryWrapper> calculation) implements Provider {
	}

	record UnaryGeometryIntegerProvider(ToIntFunction<GeometryWrapper> calculation) implements Provider {
	}

	record UnaryGeometryToDoubleProvider(ToDoubleFunction<GeometryWrapper> calculation) implements Provider {
	}
}
