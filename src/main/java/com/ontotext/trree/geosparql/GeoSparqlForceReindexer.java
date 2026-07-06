package com.ontotext.trree.geosparql;

import com.ontotext.trree.sdk.PluginConnection;

/**
 * Utility class for reindexing all indexable data.
 */
public class GeoSparqlForceReindexer {
	private final GeoSparqlIndexer indexer;
	private final GeoSparqlPlugin plugin;

	public GeoSparqlForceReindexer(GeoSparqlIndexer indexer, GeoSparqlPlugin plugin) {
		this.indexer = indexer;
		this.plugin = plugin;
	}

	public void reindex(PluginConnection pluginConnection) throws Exception {
		indexer.freshIndex();
		RepositoryGeometrySource source = new RepositoryGeometrySource(plugin, pluginConnection);
		source.forEachGeometryResource((geometryResourceId, geometries) ->
				indexer.indexGeometryList(geometryResourceId, source.subjectMapper(), geometries));
		source.forEachFeature((featureId, geometries) ->
				indexer.indexGeometryList(featureId, source.subjectMapper(), geometries));
	}
}
