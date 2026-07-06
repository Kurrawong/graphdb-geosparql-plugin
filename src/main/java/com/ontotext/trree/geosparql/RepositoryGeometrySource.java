package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.IndexGeometry;
import com.ontotext.trree.geosparql.jena.JenaGeoSparqlException;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import com.ontotext.trree.sdk.Entities;
import com.ontotext.trree.sdk.PluginConnection;
import com.ontotext.trree.sdk.PluginException;
import com.ontotext.trree.sdk.StatementIterator;
import gnu.trove.TLongHashSet;
import gnu.trove.TLongProcedure;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Repository traversal for GeoSPARQL geometry resources and Features.
 */
final class RepositoryGeometrySource {
	private final GeoSparqlPlugin plugin;
	private final PluginConnection pluginConnection;
	private final Entities entities;
	private final Map<Long, List<IndexGeometry>> geometryResourceCache = new HashMap<>();

	RepositoryGeometrySource(GeoSparqlPlugin plugin, PluginConnection pluginConnection) {
		this.plugin = plugin;
		this.pluginConnection = pluginConnection;
		this.entities = pluginConnection.getEntities();
	}

	Function<Long, String> subjectMapper() {
		return subject -> entities.get(subject).stringValue();
	}

	List<IndexGeometry> geometriesForGeometryResource(long geometryResourceId) {
		return geometryResourceCache.computeIfAbsent(geometryResourceId, this::loadGeometriesForGeometryResource);
	}

	private List<IndexGeometry> loadGeometriesForGeometryResource(long geometryResourceId) {
		List<IndexGeometry> geometries = new ArrayList<>();
		addGeometriesWithPredicate(geometryResourceId, plugin.asWKT, geometries);
		addGeometriesWithPredicate(geometryResourceId, plugin.asGML, geometries);
		return Collections.unmodifiableList(geometries);
	}

	List<IndexGeometry> geometriesForFeature(long featureId) {
		List<IndexGeometry> geometries = new ArrayList<>();
		StatementIterator iterator = pluginConnection.getStatements().get(featureId, plugin.hasDefaultGeometry, 0);
		try {
			while (iterator.next()) {
				geometries.addAll(geometriesForGeometryResource(iterator.object));
			}
		} finally {
			iterator.close();
		}
		return geometries;
	}

	void forEachGeometryResource(GeometryResourceConsumer consumer) {
		TLongHashSet geometryResources = new TLongHashSet();
		collectGeometryResources(plugin.asWKT, geometryResources);
		collectGeometryResources(plugin.asGML, geometryResources);
		geometryResources.forEach(new TLongProcedure() {
			@Override
			public boolean execute(long geometryResourceId) {
				consumer.accept(geometryResourceId, geometriesForGeometryResource(geometryResourceId));
				return true;
			}
		});
	}

	void forEachFeature(FeatureConsumer consumer) {
		TLongHashSet features = new TLongHashSet();
		StatementIterator iterator = pluginConnection.getStatements().get(0, plugin.hasDefaultGeometry, 0);
		try {
			while (iterator.next()) {
				features.add(iterator.subject);
			}
		} finally {
			iterator.close();
		}
		features.forEach(new TLongProcedure() {
			@Override
			public boolean execute(long featureId) {
				consumer.accept(featureId, geometriesForFeature(featureId));
				return true;
			}
		});
	}

	void forEachFeatureUsingGeometry(long geometryResourceId, FeatureConsumer consumer) {
		StatementIterator iterator = pluginConnection.getStatements().get(0, plugin.hasDefaultGeometry,
				geometryResourceId);
		try {
			while (iterator.next()) {
				consumer.accept(iterator.subject, geometriesForFeature(iterator.subject));
			}
		} finally {
			iterator.close();
		}
	}

	private void collectGeometryResources(long predicate, TLongHashSet geometryResources) {
		StatementIterator iterator = pluginConnection.getStatements().get(0, predicate, 0);
		try {
			while (iterator.next()) {
				geometryResources.add(iterator.subject);
			}
		} finally {
			iterator.close();
		}
	}

	private void addGeometriesWithPredicate(long geometryResourceId, long predicate, List<IndexGeometry> geometries) {
		StatementIterator iterator = pluginConnection.getStatements().get(geometryResourceId, predicate, 0);
		try {
			while (iterator.next()) {
				IndexGeometry geometry = indexGeometryFromLiteralId(geometryResourceId, iterator.object, predicate);
				if (geometry != null) {
					geometries.add(geometry);
				}
			}
		} finally {
			iterator.close();
		}
	}

	private IndexGeometry indexGeometryFromLiteralId(long subject, long object, long geometryTypeId) {
		Value value = entities.get(object);
		if (!(value instanceof Literal)) {
			return null;
		}
		IRI datatype = geometryTypeId == plugin.asGML ? GeoConstants.GEO_GML_LITERAL : GeoConstants.GEO_WKT_LITERAL;
		try {
			return plugin.getIndexGeometryFromLiteral((Literal) value, datatype);
		} catch (JenaGeoSparqlException e) {
			String subjectText = entities.get(subject).stringValue();
			if (plugin.getConfig().isIgnoreErrors()) {
				plugin.getLogger().warn("Skipping GeoSPARQL geometry for subject {} because it cannot be indexed: {}",
						subjectText, e.getMessage());
				return null;
			}
			throw new PluginException("Could not index GeoSPARQL geometry for subject " + subjectText
					+ ". If you want to skip invalid repository geometries, configure ignoreErrors = true and rebuild the index.",
					e);
		}
	}
}

interface GeometryResourceConsumer {
	void accept(long geometryResourceId, List<IndexGeometry> geometries);
}

interface FeatureConsumer {
	void accept(long featureId, List<IndexGeometry> geometries);
}
