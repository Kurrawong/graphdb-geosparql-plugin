package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that KML ordinates are written with ASCII digits and signs whatever the JVM default locale is. The
 * conversion runs in a separate JVM so the locale of the test process is not changed.
 */
public class KmlOrdinateLocaleTest {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final String RESULT_PREFIX = "KML-RESULT ";
	private static final List<Literal> SOURCES = List.of(
			VALUE_FACTORY.createLiteral("POINT(-83.38 33.95)", GeoConstants.GEO_WKT_LITERAL),
			VALUE_FACTORY.createLiteral("POINT(0.00001 -0.000001)", GeoConstants.GEO_WKT_LITERAL),
			VALUE_FACTORY.createLiteral("<LineString><coordinates>1,2,10 3,4,-20.5</coordinates></LineString>",
					GeoConstants.GEO_KML_LITERAL));

	@Test
	public void conversionUnderArabicEgyptLocaleWritesReadableAsciiOrdinates() throws Exception {
		Process process = new ProcessBuilder(
				Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-Duser.language=ar", "-Duser.country=EG",
				"-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
				ConversionProcess.class.getName())
				.start();
		assertTrue("Conversion process did not terminate", process.waitFor(60, TimeUnit.SECONDS));
		String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(error, 0, process.exitValue());
		List<String> results = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
				.lines()
				.filter(line -> line.startsWith(RESULT_PREFIX))
				.map(line -> line.substring(RESULT_PREFIX.length()))
				.toList();

		assertEquals(List.of(
				"<Point xmlns=\"http://www.opengis.net/kml/2.2\"><coordinates>-83.38,33.95</coordinates></Point>",
				"<Point xmlns=\"http://www.opengis.net/kml/2.2\"><coordinates>0.00001,-0.000001</coordinates></Point>",
				"<LineString xmlns=\"http://www.opengis.net/kml/2.2\"><coordinates>1,2,10 3,4,-20.5</coordinates>"
						+ "</LineString>"), results);
		for (String result : results) {
			Literal literal = VALUE_FACTORY.createLiteral(result, GeoConstants.GEO_KML_LITERAL);
			assertFalse(result, JenaGeometryAdapter.toSourceGeometryLiteral(literal).asGeometryWrapper().isEmpty());
		}
	}

	/** Converts each source with {@code geof:asKML} under the JVM default locale and prints prefixed compact KML lines. */
	public static final class ConversionProcess {
		public static void main(String[] arguments) throws Exception {
			for (Literal source : SOURCES) {
				Literal result = (Literal) JenaFunctionEvaluator.evaluate(VALUE_FACTORY,
						GeoConstants.GEOF_AS_KML.stringValue(), source);
				System.out.println(RESULT_PREFIX + result.stringValue().replaceAll(">\\s+<", "><").trim());
			}
		}
	}
}
