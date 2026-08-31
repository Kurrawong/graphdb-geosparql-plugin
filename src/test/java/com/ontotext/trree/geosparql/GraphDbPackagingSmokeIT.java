package com.ontotext.trree.geosparql;

import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

public class GraphDbPackagingSmokeIT {
	private static final String REPOSITORY_ID = "packaging-smoke";
	private static final String DOCKERFILE_PROPERTY = "graphdb.packagingSmoke.dockerfile";
	private static final String PLUGIN_ZIP_PROPERTY = "graphdb.packagingSmoke.pluginZip";
	private static final String SIS_DATA_DIR_PROPERTY = "graphdb.packagingSmoke.sisDataDir";
	private static final String GRAPHDB_IMAGE_PROPERTY = "graphdb.packagingSmoke.graphdbImage";
	private static final String JAVA_IMAGE_PROPERTY = "graphdb.packagingSmoke.javaImage";
	private static final String DATUM_GRID_FILENAME = "nzgd2kgrid0005.gsb";
	private static final String DATUM_GRID_RESOURCE = "datum-grids/" + DATUM_GRID_FILENAME;
	private static final String CONTAINER_DATUM_GRID_PATH =
			"/opt/graphdb/sis-data/DatumChanges/" + DATUM_GRID_FILENAME;

	/*
	 * The EPSG:3006 points reuse the GDB-10773 3-4-5 metre fixture. The surrounding polygon is the smallest
	 * additional data needed to exercise an indexed GeoSPARQL property relation with the same external CRS definition.
	 */
	private static final String INSERT_GEOMETRIES = ""
			+ "PREFIX geo: <http://www.opengis.net/ont/geosparql#>\n"
			+ "PREFIX ex: <http://example.com/packaging-smoke/>\n"
			+ "INSERT DATA {\n"
			+ "  ex:container a geo:Feature ; geo:hasDefaultGeometry ex:containerGeometry .\n"
			+ "  ex:containerGeometry a geo:Geometry ;\n"
			+ "    geo:asWKT \"POLYGON((0 0,0 4,4 4,4 0,0 0))\"^^geo:wktLiteral .\n"
			+ "  ex:thing a geo:Feature ; geo:hasDefaultGeometry ex:thingGeometry .\n"
			+ "  ex:thingGeometry a geo:Geometry ;\n"
			+ "    geo:asWKT \"POINT(1 1)\"^^geo:wktLiteral .\n"
			+ "  ex:projectedContainer a geo:Feature ; geo:hasDefaultGeometry ex:projectedContainerGeometry .\n"
			+ "  ex:projectedContainerGeometry a geo:Geometry ;\n"
			+ "    geo:asWKT \"<http://www.opengis.net/def/crs/EPSG/0/3006> "
			+ "POLYGON((521990 6703990,521990 6704010,522010 6704010,522010 6703990,521990 6703990))\""
			+ "^^geo:wktLiteral .\n"
			+ "  ex:projectedThing a geo:Feature ; geo:hasDefaultGeometry ex:projectedThingGeometry .\n"
			+ "  ex:projectedThingGeometry a geo:Geometry ;\n"
			+ "    geo:asWKT \"<http://www.opengis.net/def/crs/EPSG/0/3006> POINT(522000 6704000)\""
			+ "^^geo:wktLiteral .\n"
			+ "}";

	private static final String ENABLE_GEOSPARQL = ""
			+ "PREFIX plugin: <http://www.ontotext.com/plugins/geosparql#>\n"
			+ "INSERT DATA { [] plugin:enabled true }";

	private static final String WITHIN_QUERY = ""
			+ "PREFIX geo: <http://www.opengis.net/ont/geosparql#>\n"
			+ "PREFIX ex: <http://example.com/packaging-smoke/>\n"
			+ "ASK { ex:thing geo:sfWithin ex:container }";

	private static final String EPSG_3006_WITHIN_QUERY = ""
			+ "PREFIX geo: <http://www.opengis.net/ont/geosparql#>\n"
			+ "PREFIX ex: <http://example.com/packaging-smoke/>\n"
			+ "ASK { ex:projectedThing geo:sfWithin ex:projectedContainer }";

	private static final String EPSG_3006_DISTANCE_QUERY = ""
			+ "PREFIX geo: <http://www.opengis.net/ont/geosparql#>\n"
			+ "PREFIX geof: <http://www.opengis.net/def/function/geosparql/>\n"
			+ "PREFIX uom: <http://www.opengis.net/def/uom/OGC/1.0/>\n"
			+ "ASK {\n"
			+ "  BIND(geof:distance(\n"
			+ "    \"<http://www.opengis.net/def/crs/EPSG/0/3006> POINT Z (522000 6704000 100)\"^^geo:wktLiteral,\n"
			+ "    \"<http://www.opengis.net/def/crs/EPSG/0/3006> POINT Z (522003 6704004 100)\"^^geo:wktLiteral,\n"
			+ "    uom:metre\n"
			+ "  ) AS ?distance)\n"
			+ "  FILTER(abs(?distance - 5.0) < 0.01)\n"
			+ "}";

	/* The NZGD49 point and NZGD2000 result use LINZ's distortion-grid example 1, converted to decimal degrees. */
	private static final String NZGD49_TO_NZGD2000_GRID_QUERY = ""
			+ "PREFIX geo: <http://www.opengis.net/ont/geosparql#>\n"
			+ "PREFIX geof: <http://www.opengis.net/def/function/geosparql/>\n"
			+ "PREFIX uom: <http://www.opengis.net/def/uom/OGC/1.0/>\n"
			+ "ASK {\n"
			+ "  BIND(geof:transform(\n"
			+ "    \"<http://www.opengis.net/def/crs/EPSG/0/4272> POINT(-36.5 175)\"^^geo:wktLiteral,\n"
			+ "    <http://www.opengis.net/def/crs/EPSG/0/4167>\n"
			+ "  ) AS ?transformed)\n"
			+ "  BIND(\"<http://www.opengis.net/def/crs/EPSG/0/4167> "
			+ "POINT(-36.498190227778 175.000192969444)\"^^geo:wktLiteral AS ?expected)\n"
			+ "  FILTER(geof:distance(?transformed, ?expected, uom:metre) < 0.1)\n"
			+ "}";

	private static final Pattern TRUE_BOOLEAN_RESULT =
			Pattern.compile("\\\"boolean\\\"\\s*:\\s*true");
	private static final Pattern FALSE_BOOLEAN_RESULT =
			Pattern.compile("\\\"boolean\\\"\\s*:\\s*false");

	private static final ImageFromDockerfile IMAGE = new ImageFromDockerfile()
			.withFileFromPath("Dockerfile", requiredPath(DOCKERFILE_PROPERTY, "packaging-smoke Dockerfile"))
			.withFileFromPath("geosparql-plugin-graphdb-plugin.zip",
					requiredPath(PLUGIN_ZIP_PROPERTY, "assembled plugin ZIP"))
			.withFileFromPath("sis-data",
					requiredDirectory(SIS_DATA_DIR_PROPERTY, "packaging-smoke Apache SIS data directory"))
			.withBuildArg("GRAPHDB_IMAGE", requiredProperty(GRAPHDB_IMAGE_PROPERTY))
			.withBuildArg("JAVA_IMAGE", requiredProperty(JAVA_IMAGE_PROPERTY));

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	@Test
	public void datumGridKnownPointValidationReturnsFalseAndWarnsWhenGridIsMissing() throws Exception {
		try (GenericContainer<?> graphDb = graphDbWithoutDatumGrid()) {
			graphDb.start();
			createRepository(graphDb);

			assertAskFalse(graphDb, NZGD49_TO_NZGD2000_GRID_QUERY,
					"evaluate NZGD49 to NZGD2000 transformation without the datum grid");
			String logs = graphDb.getLogs();
			assertTrue("Expected GraphDB to report the missing NTv2 grid. Logs:\n" + logs,
					logs.contains("Cannot find NTv2 file") && logs.contains(DATUM_GRID_FILENAME));
		}
	}

	@Test
	public void assembledPluginUsesExternalCrsDataForQueriesAndDatumGridTransformation() throws Exception {
		try (GenericContainer<?> graphDb = graphDbWithDatumGrid()) {
			graphDb.start();
			createRepository(graphDb);
			executeUpdate(graphDb, INSERT_GEOMETRIES, "insert smoke-test geometries");
			executeUpdate(graphDb, ENABLE_GEOSPARQL, "enable GeoSPARQL and build its index");

			assertAskTrue(graphDb, WITHIN_QUERY, "execute indexed geo:sfWithin query");
			assertAskTrue(graphDb, EPSG_3006_WITHIN_QUERY,
					"execute EPSG:3006 indexed geo:sfWithin query");
			assertAskTrue(graphDb, EPSG_3006_DISTANCE_QUERY,
					"evaluate EPSG:3006 five-metre distance function");
			assertAskTrue(graphDb, NZGD49_TO_NZGD2000_GRID_QUERY,
					"evaluate NZGD49 to NZGD2000 datum-grid transformation");
		}
	}

	private GenericContainer<?> graphDbWithoutDatumGrid() {
		return new GenericContainer<>(IMAGE)
				.withExposedPorts(7200)
				.waitingFor(Wait.forHttp("/rest/repositories")
						.forStatusCode(200)
						.withStartupTimeout(Duration.ofMinutes(2)));
	}

	private GenericContainer<?> graphDbWithDatumGrid() {
		GenericContainer<?> graphDb = graphDbWithoutDatumGrid();
		graphDb.withCopyFileToContainer(
				MountableFile.forClasspathResource(DATUM_GRID_RESOURCE),
				CONTAINER_DATUM_GRID_PATH);
		return graphDb;
	}

	private void assertAskTrue(GenericContainer<?> graphDb, String query, String operation) throws Exception {
		HttpResponse<String> response = send(graphDb, HttpRequest.newBuilder(
				endpoint(graphDb, "/repositories/" + REPOSITORY_ID + "?query=" + encode(query)))
				.header("Accept", "application/sparql-results+json")
				.timeout(Duration.ofMinutes(2))
				.GET()
				.build(), operation);

		assertTrue(failureMessage(graphDb, "Expected the ASK query to return true", response),
				TRUE_BOOLEAN_RESULT.matcher(response.body()).find());
	}

	private void assertAskFalse(GenericContainer<?> graphDb, String query, String operation) throws Exception {
		HttpResponse<String> response = send(graphDb, HttpRequest.newBuilder(
				endpoint(graphDb, "/repositories/" + REPOSITORY_ID + "?query=" + encode(query)))
				.header("Accept", "application/sparql-results+json")
				.timeout(Duration.ofMinutes(2))
				.GET()
				.build(), operation);

		assertTrue(failureMessage(graphDb, "Expected the ASK query to return false", response),
				FALSE_BOOLEAN_RESULT.matcher(response.body()).find());
	}

	private void createRepository(GenericContainer<?> graphDb) throws Exception {
		byte[] repositoryConfig = readResource("/graphdb-packaging-smoke-repository.ttl");
		String boundary = "GraphDbPackagingSmokeBoundary";
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(("--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"config\"; "
				+ "filename=\"graphdb-packaging-smoke-repository.ttl\"\r\n"
				+ "Content-Type: text/turtle\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.write(repositoryConfig);
		body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

		send(graphDb, HttpRequest.newBuilder(endpoint(graphDb, "/rest/repositories"))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.timeout(Duration.ofMinutes(2))
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
				.build(), "create ephemeral GraphDB repository");
	}

	private void executeUpdate(GenericContainer<?> graphDb, String update, String operation) throws Exception {
		send(graphDb, HttpRequest.newBuilder(endpoint(graphDb,
				"/repositories/" + REPOSITORY_ID + "/statements"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.timeout(Duration.ofMinutes(2))
				.POST(HttpRequest.BodyPublishers.ofString("update=" + encode(update)))
				.build(), operation);
	}

	private HttpResponse<String> send(GenericContainer<?> graphDb, HttpRequest request, String operation)
			throws Exception {
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		assertTrue(failureMessage(graphDb, "Failed to " + operation, response),
				response.statusCode() >= 200 && response.statusCode() < 300);
		return response;
	}

	private URI endpoint(GenericContainer<?> graphDb, String path) {
		return URI.create("http://" + graphDb.getHost() + ":" + graphDb.getMappedPort(7200) + path);
	}

	private String failureMessage(GenericContainer<?> graphDb, String message, HttpResponse<String> response) {
		return message + ". HTTP status: " + response.statusCode()
				+ ", response body: " + response.body()
				+ "\nGraphDB container logs:\n" + graphDb.getLogs();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static byte[] readResource(String resourceName) throws IOException {
		try (InputStream input = GraphDbPackagingSmokeIT.class.getResourceAsStream(resourceName)) {
			if (input == null) {
				throw new IllegalStateException("Missing test resource: " + resourceName);
			}
			return input.readAllBytes();
		}
	}

	private static Path requiredPath(String propertyName, String description) {
		Path path = Paths.get(requiredProperty(propertyName)).toAbsolutePath().normalize();
		if (!Files.isRegularFile(path)) {
			throw new IllegalStateException("Missing " + description + " at " + path
					+ ". Run this test with mvn -Pgraphdb-packaging-smoke verify.");
		}
		return path;
	}

	private static Path requiredDirectory(String propertyName, String description) {
		Path path = Paths.get(requiredProperty(propertyName)).toAbsolutePath().normalize();
		if (!Files.isDirectory(path)) {
			throw new IllegalStateException("Missing " + description + " at " + path
					+ ". Run this test with mvn -Pgraphdb-packaging-smoke verify.");
		}
		return path;
	}

	private static String requiredProperty(String propertyName) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalStateException("Missing required system property: " + propertyName
					+ ". Run this test with mvn -Pgraphdb-packaging-smoke verify.");
		}
		return value;
	}
}
