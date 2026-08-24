package com.ontotext.trree.geosparql;

import com.ontotext.test.TemporaryLocalFolder;
import com.ontotext.trree.geosparql.lucene.LuceneGeoIndexer;
import com.ontotext.trree.geosparql.util.GeoSparqlUtils;
import com.ontotext.trree.sdk.PluginException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.FSDirectory;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Process-crash coverage for the plugin transaction invariant from
 * https://github.com/Kurrawong/graphdb-geosparql-plugin/issues/2.
 */
public class GeoSparqlProcessCrashRecoveryTest {
	@Rule
	public TemporaryLocalFolder tmpFolder = new TemporaryLocalFolder();

	@Test
	public void restartFailsClosedAfterPendingMarkerCreation() throws Exception {
		assertRestartFailsClosed("AFTER_MARKER", false);
	}

	@Test
	public void restartFailsClosedAfterConfigurationReplacement() throws Exception {
		assertRestartFailsClosed("AFTER_CONFIG_REPLACEMENT", true);
	}

	@Test
	public void restartFailsClosedAfterProvisionalLuceneCommit() throws Exception {
		assertRestartFailsClosed("AFTER_PROVISIONAL_COMMIT", true);
	}

	@Test
	public void restartFailsClosedAfterGraphDbCommitBeforeMarkerRemoval() throws Exception {
		assertRestartFailsClosed("AFTER_GRAPHDB_COMMIT", true);
	}

	@Test
	public void restartFailsClosedDuringAbortRestoration() throws Exception {
		assertRestartFailsClosed("DURING_ABORT_RESTORATION", false);
	}

	private void assertRestartFailsClosed(String boundary, boolean expectedEnabled) throws Exception {
		Path dataDir = tmpFolder.newFolder(boundary.toLowerCase()).toPath();
		Process process = new ProcessBuilder(
				Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-cp", testClassPath(), GeoSparqlCrashProcess.class.getName(), boundary, dataDir.toString())
				.redirectErrorStream(true)
				.start();
		assertTrue("Crash process did not terminate", process.waitFor(30, TimeUnit.SECONDS));
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(output, GeoSparqlCrashProcess.HALT_CODE, process.exitValue());

		GeoSparqlConfig persistedConfig = GeoSparqlUtils.readConfig(dataDir);
		assertEquals(expectedEnabled, persistedConfig.isEnabled());
		assertTrue(Files.exists(GeoSparqlTransactionMarker.resolvePath(dataDir)));
		assertEquals(hasProvisionalCommit(boundary) ? "POINT(2 2)" : "POINT(1 1)",
				committedSourceLexicalForm(dataDir));

		LuceneGeoIndexer restarted = new LuceneGeoIndexer(
				GeoSparqlCrashProcess.plugin(dataDir, persistedConfig));
		restarted.initialize();
		PluginException failure = assertThrows(PluginException.class,
				() -> restarted.getSourceGeometryLiteralsFor(0));
		assertTrue(failure.getMessage().contains("pending GraphDB transaction"));
		assertTrue(failure.getMessage().contains("force-reindex"));
	}

	private String testClassPath() {
		return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
	}

	private boolean hasProvisionalCommit(String boundary) {
		return "AFTER_PROVISIONAL_COMMIT".equals(boundary)
				|| "AFTER_GRAPHDB_COMMIT".equals(boundary)
				|| "DURING_ABORT_RESTORATION".equals(boundary);
	}

	private String committedSourceLexicalForm(Path dataDir) throws Exception {
		try (FSDirectory directory = FSDirectory.open(GeoSparqlConfig.resolveIndexPath(dataDir));
				DirectoryReader reader = DirectoryReader.open(directory)) {
			return reader.document(0).get("geoExactLexicalForm");
		}
	}
}
