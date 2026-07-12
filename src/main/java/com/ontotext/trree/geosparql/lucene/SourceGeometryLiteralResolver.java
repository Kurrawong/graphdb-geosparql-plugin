package com.ontotext.trree.geosparql.lucene;

import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.jena.JenaGeoSparqlException;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Reuses validated source geometry literal snapshots within one entity traversal.
 */
final class SourceGeometryLiteralResolver {
	@FunctionalInterface
	interface Loader {
		SourceGeometryLiteral load(StoredSourceGeometryIdentity identity);
	}

	private final Loader loader;
	private final Map<StoredSourceGeometryIdentity, SourceGeometryLiteral> cache = new HashMap<>();

	SourceGeometryLiteralResolver() {
		this(SourceGeometryLiteralResolver::loadAndValidate);
	}

	SourceGeometryLiteralResolver(Loader loader) {
		this.loader = loader;
	}

	SourceGeometryLiteral resolve(StoredSourceGeometryIdentity identity) {
		SourceGeometryLiteral source = cache.get(identity);
		if (source == null) {
			source = loader.load(identity);
			cache.put(identity, source);
		}
		return source;
	}

	void clear() {
		cache.clear();
	}

	private static SourceGeometryLiteral loadAndValidate(StoredSourceGeometryIdentity identity) {
		SimpleValueFactory values = SimpleValueFactory.getInstance();
		SourceGeometryLiteral source = SourceGeometryLiteral.fromLiteral(values.createLiteral(
				identity.lexicalForm(), values.createIRI(identity.datatype())));
		if (!source.effectiveCrsUri().equals(identity.effectiveSourceCrsUri())) {
			throw new JenaGeoSparqlException("Lucene source geometry literal CRS metadata does not match "
					+ "effective source CRS.");
		}
		return source;
	}
}
