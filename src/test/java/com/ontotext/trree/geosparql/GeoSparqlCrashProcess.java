package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.lucene.LuceneGeoIndexer;
import com.ontotext.trree.geosparql.util.GeoSparqlUtils;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/** Process entry point used to terminate at persistent GeoSPARQL transaction boundaries. */
public final class GeoSparqlCrashProcess {
	static final int HALT_CODE = 23;

	private GeoSparqlCrashProcess() {
	}

	public static void main(String[] arguments) {
		try {
			Path dataDir = Path.of(arguments[1]);
			LuceneGeoIndexer indexer = createOldState(dataDir);
			GeoSparqlConfig config = GeoSparqlUtils.readConfig(dataDir);
			GeoSparqlTransactionMarker marker = new GeoSparqlTransactionMarker(dataDir);

			marker.create();
			if (!"AFTER_MARKER".equals(arguments[0])) {
				config.setEnabled(true);
				GeoSparqlUtils.saveConfig(config, dataDir);
			}
			if ("AFTER_PROVISIONAL_COMMIT".equals(arguments[0])
					|| "AFTER_GRAPHDB_COMMIT".equals(arguments[0])
					|| "DURING_ABORT_RESTORATION".equals(arguments[0])) {
				indexer.begin();
				indexer.freshIndex();
				indexer.indexGeometryList(2L, id -> "new-geometry",
						List.of(TestIndexGeometries.fromWkt("POINT(2 2)")));
				indexer.commit();
			}
			if ("DURING_ABORT_RESTORATION".equals(arguments[0])) {
				config.setEnabled(false);
				GeoSparqlUtils.saveConfig(config, dataDir);
			}

			Runtime.getRuntime().halt(HALT_CODE);
		} catch (Throwable failure) {
			failure.printStackTrace(System.err);
			Runtime.getRuntime().halt(24);
		}
	}

	private static LuceneGeoIndexer createOldState(Path dataDir) throws Exception {
		GeoSparqlConfig config = new GeoSparqlConfig();
		config.setEnabled(true);
		GeoSparqlUtils.saveConfig(config, dataDir);
		GeoSparqlPlugin plugin = plugin(dataDir, config);
		LuceneGeoIndexer indexer = new LuceneGeoIndexer(plugin);
		indexer.initialize();
		indexer.begin();
		indexer.indexGeometryList(1L, id -> "old-geometry",
				List.of(TestIndexGeometries.fromWkt("POINT(1 1)")));
		indexer.commit();
		indexer.complete();
		config.setEnabled(false);
		GeoSparqlUtils.saveConfig(config, dataDir);
		return indexer;
	}

	static GeoSparqlPlugin plugin(Path dataDir, GeoSparqlConfig config) {
		GeoSparqlPlugin plugin = new GeoSparqlPlugin();
		plugin.setConfig(config);
		plugin.setDataDir(dataDir.toFile());
		plugin.setLogger(LoggerFactory.getLogger(GeoSparqlCrashProcess.class));
		return plugin;
	}
}
