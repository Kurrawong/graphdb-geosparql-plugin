package com.ontotext.trree.geosparql;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CrsDataEnvironmentTest {
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void nullSisDataIsUnset() {
		CrsDataEnvironment environment = CrsDataEnvironment.inspect(null, path -> true, path -> true);

		assertEquals(CrsDataEnvironment.Status.UNSET, environment.status());
		assertTrue(environment.message().contains("SIS_DATA is not set"));
		assertTrue(environment.message().contains("CRS84/default GeoSPARQL data is supported"));
	}

	@Test
	public void blankSisDataIsUnset() {
		CrsDataEnvironment environment = CrsDataEnvironment.inspect(" \t ", path -> true, path -> true);

		assertEquals(CrsDataEnvironment.Status.UNSET, environment.status());
		assertTrue(environment.message().contains("SIS_DATA is not set"));
	}

	@Test
	public void readableDirectoryIsConfigured() throws Exception {
		File sisDataDirectory = temporaryFolder.newFolder("sis-data");

		CrsDataEnvironment environment = CrsDataEnvironment.inspect(sisDataDirectory.getAbsolutePath(),
				path -> path.toFile().isDirectory(), path -> path.toFile().canRead());

		assertEquals(CrsDataEnvironment.Status.CONFIGURED, environment.status());
		assertTrue(environment.message().contains("SIS_DATA=" + sisDataDirectory.getAbsolutePath()));
		assertTrue(environment.message().contains("readable directory"));
	}

	@Test
	public void existingFileIsUnusable() throws Exception {
		File sisDataFile = temporaryFolder.newFile("sis-data-file");

		CrsDataEnvironment environment = CrsDataEnvironment.inspect(sisDataFile.getAbsolutePath(),
				path -> path.toFile().isDirectory(), path -> path.toFile().canRead());

		assertEquals(CrsDataEnvironment.Status.UNUSABLE, environment.status());
		assertTrue(environment.message().contains("does not point to a directory"));
	}

	@Test
	public void missingPathIsUnusable() {
		File missingPath = new File(temporaryFolder.getRoot(), "missing");

		CrsDataEnvironment environment = CrsDataEnvironment.inspect(missingPath.getAbsolutePath(),
				path -> path.toFile().isDirectory(), path -> path.toFile().canRead());

		assertEquals(CrsDataEnvironment.Status.UNUSABLE, environment.status());
		assertTrue(environment.message().contains("does not point to a directory"));
	}

	@Test
	public void unreadableDirectoryIsUnusable() {
		CrsDataEnvironment environment = CrsDataEnvironment.inspect("/opt/graphdb/sis-data",
				path -> true, path -> false);

		assertEquals(CrsDataEnvironment.Status.UNUSABLE, environment.status());
		assertTrue(environment.message().contains("is not readable"));
	}

	@Test
	public void invalidPathIsUnusable() {
		CrsDataEnvironment environment = CrsDataEnvironment.inspect("bad\0path", path -> true, path -> true);

		assertEquals(CrsDataEnvironment.Status.UNUSABLE, environment.status());
		assertTrue(environment.message().contains("not a valid filesystem path"));
	}
}
