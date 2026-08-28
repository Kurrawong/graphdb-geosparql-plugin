package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.GeoJsonResultDimensionPolicy;
import com.ontotext.trree.geosparql.jena.query.GeometryArea;
import com.ontotext.trree.geosparql.jena.query.GeometryBoundingCircle;
import com.ontotext.trree.geosparql.jena.query.GeometryCentroid;
import com.ontotext.trree.geosparql.jena.query.GeometryConcaveHull;
import com.ontotext.trree.geosparql.jena.query.GeometryCount;
import com.ontotext.trree.geosparql.jena.query.GeometryCoordinateExtrema;
import com.ontotext.trree.geosparql.jena.query.GeometryLength;
import com.ontotext.trree.geosparql.jena.query.GeometryMember;
import com.ontotext.trree.geosparql.jena.query.GeometryMetadata;
import com.ontotext.trree.geosparql.jena.query.MetricBuffer;
import com.ontotext.trree.geosparql.jena.query.MetricDistance;
import com.ontotext.trree.geosparql.jena.query.TopologicalDimension;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.GeometryWrapper;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

final class QueryFunctionManifest {
	private static final List<Entry> ENTRIES = List.of(
			new Entry(GeoConstants.GEOF_AREA.stringValue(), Requirement.R40, 2,
					new UnaryGeometryUnitToDoubleProvider(GeometryArea::calculate)),
			new Entry(GeoConstants.GEOF_BOUNDARY.stringValue(), Requirement.R39, 1,
					new UnaryGeometryProvider(GeometryWrapper::boundary,
							GeoJsonResultDimensionPolicy.PRESERVE_DEFINED_Z)),
			new Entry(GeoConstants.GEOF_BOUNDING_CIRCLE.stringValue(), Requirement.R39, 1,
					new UnaryGeometryProvider(GeometryBoundingCircle::calculate,
							GeoJsonResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_BUFFER.stringValue(), Requirement.R39, 3,
					new UnaryGeometryDoubleUnitToGeometryProvider(GeometryWrapper::buffer,
							GeoJsonResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_CENTROID.stringValue(), Requirement.R39, 1,
					new UnaryGeometryProvider(GeometryCentroid::calculate,
							GeoJsonResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_CONCAVE_HULL.stringValue(), Requirement.R39, 1,
					new UnaryGeometryProvider(GeometryConcaveHull::calculate,
							GeoJsonResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_CONVEX_HULL.stringValue(), Requirement.R39, 1,
					new UnaryGeometryProvider(GeometryWrapper::convexHull,
							GeoJsonResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_DIFFERENCE.stringValue(), Requirement.R39, 2,
					new BinaryGeometryProvider(GeometryWrapper::difference,
							GeoJsonResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_DISTANCE.stringValue(), Requirement.R39, 3,
					new BinaryGeometryUnitToDoubleProvider(GeometryWrapper::distance)),
			new Entry(GeoConstants.GEOF_ENVELOPE.stringValue(), Requirement.R39, 1,
					new UnaryGeometryProvider(GeometryWrapper::envelope,
							GeoJsonResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_INTERSECTION.stringValue(), Requirement.R39, 2,
					new BinaryGeometryProvider(GeometryWrapper::intersection,
							GeoJsonResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_LENGTH.stringValue(), Requirement.R40, 2,
					new UnaryGeometryUnitToDoubleProvider(GeometryLength::calculate)),
			new Entry(GeoConstants.GEOF_METRIC_AREA.stringValue(), Requirement.R40, 1,
					new UnaryGeometryToDoubleProvider(GeometryArea::calculateMetric)),
			new Entry(GeoConstants.GEOF_METRIC_BUFFER.stringValue(), Requirement.R39, 2,
					new UnaryGeometryDoubleToGeometryProvider(MetricBuffer::calculate,
							GeoJsonResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_METRIC_DISTANCE.stringValue(), Requirement.R39, 2,
					new BinaryGeometryToDoubleProvider(MetricDistance::calculate)),
			new Entry(GeoConstants.GEOF_METRIC_LENGTH.stringValue(), Requirement.R40, 1,
					new UnaryGeometryToDoubleProvider(GeometryLength::calculateMetric)),
			new Entry(GeoConstants.GEOF_METRIC_PERIMETER.stringValue(), Requirement.R40, 1,
					new UnaryGeometryToDoubleProvider(GeometryLength::calculateMetric)),
			new Entry(GeoConstants.GEOF_PERIMETER.stringValue(), Requirement.R40, 2,
					new UnaryGeometryUnitToDoubleProvider(GeometryLength::calculate)),
			new Entry(GeoConstants.GEOF_MAX_X.stringValue(), Requirement.R40, 1,
					new UnaryGeometryToDoubleProvider(GeometryCoordinateExtrema::maxX)),
			new Entry(GeoConstants.GEOF_MAX_Y.stringValue(), Requirement.R40, 1,
					new UnaryGeometryToDoubleProvider(GeometryCoordinateExtrema::maxY)),
			new Entry(GeoConstants.GEOF_MAX_Z.stringValue(), Requirement.R40, 1,
					new UnaryGeometryToDoubleProvider(GeometryCoordinateExtrema::maxZ)),
			new Entry(GeoConstants.GEOF_MIN_X.stringValue(), Requirement.R40, 1,
					new UnaryGeometryToDoubleProvider(GeometryCoordinateExtrema::minX)),
			new Entry(GeoConstants.GEOF_MIN_Y.stringValue(), Requirement.R40, 1,
					new UnaryGeometryToDoubleProvider(GeometryCoordinateExtrema::minY)),
			new Entry(GeoConstants.GEOF_MIN_Z.stringValue(), Requirement.R40, 1,
					new UnaryGeometryToDoubleProvider(GeometryCoordinateExtrema::minZ)),
			new Entry(GeoConstants.GEOF_COORDINATE_DIMENSION.stringValue(), Requirement.R39, 1,
					new UnaryGeometryIntegerProvider(GeometryWrapper::getCoordinateDimension)),
			new Entry(GeoConstants.GEOF_DIMENSION.stringValue(), Requirement.R39, 1,
					new UnaryGeometryIntegerProvider(TopologicalDimension::calculate)),
			new Entry(GeoConstants.GEOF_GEOMETRY_N.stringValue(), Requirement.R40, 2,
					new GeometryMemberProvider(GeometryMember::calculate,
							GeoJsonResultDimensionPolicy.PRESERVE_DEFINED_Z)),
			new Entry(GeoConstants.GEOF_GEOMETRY_TYPE.stringValue(), Requirement.R39, 1,
					new UnaryGeometryAnyUriProvider(GeometryMetadata::simpleFeaturesTypeUri)),
			new Entry(GeoConstants.GEOF_IS_3D.stringValue(), Requirement.R39, 1,
					new UnaryGeometryBooleanProvider(GeometryMetadata::is3D)),
			new Entry(GeoConstants.GEOF_IS_EMPTY.stringValue(), Requirement.R39, 1,
					new UnaryGeometryBooleanProvider(GeometryWrapper::isEmpty)),
			new Entry(GeoConstants.GEOF_IS_MEASURED.stringValue(), Requirement.R39, 1,
					new UnaryGeometryBooleanProvider(GeometryMetadata::isMeasured)),
			new Entry(GeoConstants.GEOF_IS_SIMPLE.stringValue(), Requirement.R39, 1,
					new UnaryGeometryBooleanProvider(GeometryWrapper::isSimple)),
			new Entry(GeoConstants.GEOF_NUM_GEOMETRIES.stringValue(), Requirement.R40, 1,
					new UnaryGeometryIntegerProvider(GeometryCount::calculate)),
			new Entry(GeoConstants.GEOF_SPATIAL_DIMENSION.stringValue(), Requirement.R39, 1,
					new UnaryGeometryIntegerProvider(GeometryWrapper::getSpatialDimension)),
			new Entry(GeoConstants.GEOF_SYM_DIFFERENCE.stringValue(), Requirement.R39, 2,
					new BinaryGeometryProvider(GeometryWrapper::symDifference,
							GeoJsonResultDimensionPolicy.XY_ONLY)),
			new Entry(GeoConstants.GEOF_TRANSFORM.stringValue(), Requirement.R39, 2,
					new GeometryTargetSrsProvider(GeometryWrapper::transform,
							GeoJsonResultDimensionPolicy.PRESERVE_DEFINED_Z)),
			new Entry(GeoConstants.GEOF_UNION.stringValue(), Requirement.R39, 2,
					new BinaryGeometryProvider(GeometryWrapper::union,
							GeoJsonResultDimensionPolicy.XY_ONLY)));

	private QueryFunctionManifest() {
	}

	static List<Entry> entries() {
		return ENTRIES;
	}

	enum Requirement {
		R39,
		R40
	}

	record Entry(String uri, Requirement requirement, int mandatoryArity, Provider provider) {
	}

	sealed interface Provider permits BinaryGeometryProvider, BinaryGeometryToDoubleProvider,
			BinaryGeometryUnitToDoubleProvider,
			GeometryMemberProvider, GeometryTargetSrsProvider, UnaryGeometryAnyUriProvider,
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
			GeoJsonResultDimensionPolicy geoJsonResultDimensionPolicy) implements Provider {
	}

	@FunctionalInterface
	interface BinaryGeometryToDoubleCalculation {
		double apply(GeometryWrapper left, GeometryWrapper right) throws Exception;
	}

	record BinaryGeometryToDoubleProvider(BinaryGeometryToDoubleCalculation calculation) implements Provider {
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
			GeoJsonResultDimensionPolicy geoJsonResultDimensionPolicy)
			implements Provider {
	}

	@FunctionalInterface
	interface UnaryGeometryDoubleUnitToGeometryCalculation {
		GeometryWrapper apply(GeometryWrapper geometry, double value, String unitUri) throws Exception;
	}

	record UnaryGeometryDoubleUnitToGeometryProvider(UnaryGeometryDoubleUnitToGeometryCalculation calculation,
			GeoJsonResultDimensionPolicy geoJsonResultDimensionPolicy)
			implements Provider {
	}

	@FunctionalInterface
	interface UnaryGeometryCalculation {
		GeometryWrapper apply(GeometryWrapper geometry) throws Exception;
	}

	record UnaryGeometryProvider(UnaryGeometryCalculation calculation,
			GeoJsonResultDimensionPolicy geoJsonResultDimensionPolicy) implements Provider {
	}

	@FunctionalInterface
	interface UnaryGeometryUnitToDoubleCalculation {
		double apply(GeometryWrapper geometry, String unitUri) throws Exception;
	}

	record UnaryGeometryUnitToDoubleProvider(UnaryGeometryUnitToDoubleCalculation calculation) implements Provider {
	}

	record GeometryMemberProvider(BiFunction<GeometryWrapper, Integer, GeometryWrapper> calculation,
			GeoJsonResultDimensionPolicy geoJsonResultDimensionPolicy)
			implements Provider {
	}

	@FunctionalInterface
	interface GeometryTargetSrsCalculation {
		GeometryWrapper apply(GeometryWrapper geometry, String targetSrsUri) throws Exception;
	}

	record GeometryTargetSrsProvider(GeometryTargetSrsCalculation calculation,
			GeoJsonResultDimensionPolicy geoJsonResultDimensionPolicy) implements Provider {
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
