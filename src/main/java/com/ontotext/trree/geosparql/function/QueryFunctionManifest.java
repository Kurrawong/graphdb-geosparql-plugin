package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.query.GeometryCount;
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
			new Entry(GeoConstants.GEOF_BUFFER.stringValue(), 3,
					new UnaryGeometryDoubleUnitToGeometryProvider(GeometryWrapper::buffer)),
			new Entry(GeoConstants.GEOF_DISTANCE.stringValue(), 3,
					new BinaryGeometryUnitToDoubleProvider(GeometryWrapper::distance)),
			new Entry(GeoConstants.GEOF_LENGTH.stringValue(), 2,
					new UnaryGeometryUnitToDoubleProvider(GeometryLength::calculate)),
			new Entry(GeoConstants.GEOF_METRIC_BUFFER.stringValue(), 2,
					new UnaryGeometryDoubleToGeometryProvider(MetricBuffer::calculate)),
			new Entry(GeoConstants.GEOF_METRIC_DISTANCE.stringValue(), 2,
					new BinaryGeometryToDoubleProvider(MetricDistance::calculate)),
			new Entry(GeoConstants.GEOF_METRIC_LENGTH.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(GeometryLength::calculateMetric)),
			new Entry(GeoConstants.GEOF_METRIC_PERIMETER.stringValue(), 1,
					new UnaryGeometryToDoubleProvider(GeometryLength::calculateMetric)),
			new Entry(GeoConstants.GEOF_PERIMETER.stringValue(), 2,
					new UnaryGeometryUnitToDoubleProvider(GeometryLength::calculate)),
			new Entry(GeoConstants.GEOF_COORDINATE_DIMENSION.stringValue(), 1,
					new UnaryGeometryIntegerProvider(GeometryWrapper::getCoordinateDimension)),
			new Entry(GeoConstants.GEOF_DIMENSION.stringValue(), 1,
					new UnaryGeometryIntegerProvider(TopologicalDimension::calculate)),
			new Entry(GeoConstants.GEOF_GEOMETRY_N.stringValue(), 2,
					new GeometryMemberProvider(GeometryMember::calculate)),
			new Entry(GeoConstants.GEOF_GEOMETRY_TYPE.stringValue(), 1,
					new UnaryGeometryAnyUriProvider(GeometryMetadata::simpleFeaturesTypeUri)),
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
					new UnaryGeometryIntegerProvider(GeometryWrapper::getSpatialDimension)));

	private QueryFunctionManifest() {
	}

	static List<Entry> entries() {
		return ENTRIES;
	}

	record Entry(String uri, int mandatoryArity, Provider provider) {
	}

	sealed interface Provider permits BinaryGeometryToDoubleProvider, BinaryGeometryUnitToDoubleProvider,
			GeometryMemberProvider, UnaryGeometryAnyUriProvider, UnaryGeometryBooleanProvider,
			UnaryGeometryDoubleToGeometryProvider, UnaryGeometryDoubleUnitToGeometryProvider,
			UnaryGeometryIntegerProvider, UnaryGeometryToDoubleProvider,
			UnaryGeometryUnitToDoubleProvider {
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

	record UnaryGeometryDoubleToGeometryProvider(UnaryGeometryDoubleToGeometryCalculation calculation)
			implements Provider {
	}

	@FunctionalInterface
	interface UnaryGeometryDoubleUnitToGeometryCalculation {
		GeometryWrapper apply(GeometryWrapper geometry, double value, String unitUri) throws Exception;
	}

	record UnaryGeometryDoubleUnitToGeometryProvider(UnaryGeometryDoubleUnitToGeometryCalculation calculation)
			implements Provider {
	}

	@FunctionalInterface
	interface UnaryGeometryUnitToDoubleCalculation {
		double apply(GeometryWrapper geometry, String unitUri) throws Exception;
	}

	record UnaryGeometryUnitToDoubleProvider(UnaryGeometryUnitToDoubleCalculation calculation) implements Provider {
	}

	record GeometryMemberProvider(BiFunction<GeometryWrapper, Integer, GeometryWrapper> calculation)
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
