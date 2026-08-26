package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.query.TopologicalDimension;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.GeometryWrapper;

import java.util.List;
import java.util.function.ToIntFunction;

final class QueryFunctionManifest {
	private static final List<Entry> ENTRIES = List.of(
			new Entry(GeoConstants.GEOF_DIMENSION.stringValue(), 1,
					new UnaryGeometryIntegerProvider(TopologicalDimension::calculate)));

	private QueryFunctionManifest() {
	}

	static List<Entry> entries() {
		return ENTRIES;
	}

	record Entry(String uri, int mandatoryArity, Provider provider) {
	}

	sealed interface Provider permits UnaryGeometryIntegerProvider {
	}

	record UnaryGeometryIntegerProvider(ToIntFunction<GeometryWrapper> calculation) implements Provider {
	}
}
