package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.query.GeometryCount;
import com.ontotext.trree.geosparql.jena.query.GeometryMember;
import com.ontotext.trree.geosparql.jena.query.TopologicalDimension;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.GeometryWrapper;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

final class QueryFunctionManifest {
	private static final List<Entry> ENTRIES = List.of(
			new Entry(GeoConstants.GEOF_DIMENSION.stringValue(), 1,
					new UnaryGeometryIntegerProvider(TopologicalDimension::calculate)),
			new Entry(GeoConstants.GEOF_GEOMETRY_N.stringValue(), 2,
					new GeometryMemberProvider(GeometryMember::calculate)),
			new Entry(GeoConstants.GEOF_NUM_GEOMETRIES.stringValue(), 1,
					new UnaryGeometryIntegerProvider(GeometryCount::calculate)));

	private QueryFunctionManifest() {
	}

	static List<Entry> entries() {
		return ENTRIES;
	}

	record Entry(String uri, int mandatoryArity, Provider provider) {
	}

	sealed interface Provider permits GeometryMemberProvider, UnaryGeometryIntegerProvider {
	}

	record GeometryMemberProvider(BiFunction<GeometryWrapper, Integer, GeometryWrapper> calculation)
			implements Provider {
	}

	record UnaryGeometryIntegerProvider(ToIntFunction<GeometryWrapper> calculation) implements Provider {
	}
}
