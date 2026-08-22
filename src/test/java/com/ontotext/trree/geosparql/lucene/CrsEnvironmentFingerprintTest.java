package com.ontotext.trree.geosparql.lucene;

import com.ontotext.test.TemporaryLocalFolder;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class CrsEnvironmentFingerprintTest {
	@Rule
	public TemporaryLocalFolder tmpFolder = new TemporaryLocalFolder();

	@Test
	public void fingerprintUsesGridContentButNotDataDirectoryLocationOrDatabaseLogs() throws Exception {
		Path first = tmpFolder.newFolder("first-sis-data").toPath();
		Path second = tmpFolder.newFolder("second-sis-data").toPath();
		write(first.resolve("DatumChanges/grid.bin"), "grid-a");
		write(second.resolve("DatumChanges/grid.bin"), "grid-a");
		write(first.resolve("Databases/SpatialMetadata/db.lck"), "lock-a");
		write(second.resolve("Databases/SpatialMetadata/db.lck"), "lock-b");

		String firstFingerprint = CrsEnvironmentFingerprint.compute("SIS 1.6", "EPSG 12.013", first);
		String secondFingerprint = CrsEnvironmentFingerprint.compute("SIS 1.6", "EPSG 12.013", second);
		assertEquals(firstFingerprint, secondFingerprint);

		Files.writeString(second.resolve("DatumChanges/grid.bin"), "grid-b");
		assertNotEquals(firstFingerprint,
				CrsEnvironmentFingerprint.compute("SIS 1.6", "EPSG 12.013", second));
	}

	private void write(Path path, String contents) throws Exception {
		Files.createDirectories(path.getParent());
		Files.writeString(path, contents);
	}
}
