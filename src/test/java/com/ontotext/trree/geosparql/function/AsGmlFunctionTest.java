package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.function.Function;
import org.eclipse.rdf4j.query.algebra.evaluation.function.FunctionRegistry;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Verifies conversion to reusable GML 3.2 geometry fragments selected by the supported profile. */
public class AsGmlFunctionTest {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();
	private static final ValueFactoryTripleSource TRIPLE_SOURCE = new ValueFactoryTripleSource(VALUE_FACTORY);
	private static final IRI AS_GML = VALUE_FACTORY.createIRI(
			"http://www.opengis.net/def/function/geosparql/asGML");
	private static final Literal GML_SF0_PROFILE = VALUE_FACTORY.createLiteral(
			"http://www.opengis.net/def/profile/ogc/2.0/gml-sf0");
	private static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
	private static final String EPSG_4326 = "http://www.opengis.net/def/crs/EPSG/0/4326";
	private static final String EPSG_32634 = "http://www.opengis.net/def/crs/EPSG/0/32634";
	private static final String EPSG_4979 = "http://www.opengis.net/def/crs/EPSG/0/4979";
	private static final String GML_NAMESPACE = "http://www.opengis.net/gml/3.2";
	private static final Set<String> GML_GEOMETRY_ELEMENTS = Set.of(
			"Point", "LineString", "Polygon", "MultiPoint", "MultiCurve", "MultiSurface",
			"MultiGeometry");
	private static Schema gmlSchema;

	@BeforeClass
	public static void registerFunctions() throws Exception {
		GeoSparqlFunctionRegistration.registerAll();
		URL schemaResource = AsGmlFunctionTest.class.getClassLoader().getResource("gml/3.2.1/gml.xsd");
		assertNotNull("GML 3.2.1 schema resource", schemaResource);
		SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar");
		gmlSchema = schemaFactory.newSchema(schemaResource);
	}

	@Test
	public void supportedProfileConvertsWktToReusableGmlLiteralThroughRegisteredFunction() throws Exception {
		Literal source = VALUE_FACTORY.createLiteral("POINT(1 2)", GeoConstants.GEO_WKT_LITERAL);

		Literal result = evaluate(source, GML_SF0_PROFILE);

		assertEquals(GeoConstants.GEO_GML_LITERAL, result.getDatatype());
		assertTrue(result.stringValue().startsWith(
				"<gml:Point xmlns:gml=\"http://www.opengis.net/gml/3.2\""));
		assertTrue(JenaGeometryAdapter.toSourceGeometryLiteral(source).asGeometryWrapper().getParsingGeometry()
				.equalsExact(JenaGeometryAdapter.toSourceGeometryLiteral(result)
						.asGeometryWrapper().getParsingGeometry()));
	}

	@Test
	public void profileSelectorRequiresTheExactStringLiteralContract() throws Exception {
		Literal source = VALUE_FACTORY.createLiteral("POINT(1 2)", GeoConstants.GEO_WKT_LITERAL);
		String profile = GML_SF0_PROFILE.stringValue();

		assertEquals(GeoConstants.GEO_GML_LITERAL,
				evaluate(source, VALUE_FACTORY.createLiteral(profile, XSD.STRING)).getDatatype());
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(source, VALUE_FACTORY.createLiteral(profile, "en")));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(source, VALUE_FACTORY.createLiteral(profile, XSD.ANYURI)));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(source, VALUE_FACTORY.createIRI(profile)));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(source, VALUE_FACTORY.createLiteral(profile + "/other")));
		assertThrows(ValueExprEvaluationException.class, () -> evaluate(source));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(source, GML_SF0_PROFILE, VALUE_FACTORY.createLiteral("extra")));
	}

	@Test
	public void outputDimensionsMustAgreeWithTheSourceCrs() throws Exception {
		Literal xyz = VALUE_FACTORY.createLiteral(
				"<" + EPSG_4979 + "> POINT Z(-27.47 153.03 55)", GeoConstants.GEO_WKT_LITERAL);

		Literal result = evaluate(xyz, GML_SF0_PROFILE);

		assertEquals(EPSG_4979,
				JenaGeometryAdapter.toSourceGeometryLiteral(result).effectiveCrsUri());
		assertEquals(3, JenaGeometryAdapter.toSourceGeometryLiteral(result)
				.asGeometryWrapper().getCoordinateDimension());
		gmlSchema.newValidator().validate(new StreamSource(new StringReader(
				withDocumentGeometryIds(parseXml(result.stringValue())))));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(VALUE_FACTORY.createLiteral("POINT Z(1 2 3)", GeoConstants.GEO_WKT_LITERAL),
						GML_SF0_PROFILE));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(VALUE_FACTORY.createLiteral("POINT M(1 2 9)", GeoConstants.GEO_WKT_LITERAL),
						GML_SF0_PROFILE));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(VALUE_FACTORY.createLiteral("POINT ZM(1 2 3 9)", GeoConstants.GEO_WKT_LITERAL),
						GML_SF0_PROFILE));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(VALUE_FACTORY.createLiteral(
						"{\"type\":\"Point\",\"coordinates\":[1,2,3]}", GeoConstants.GEO_JSON_LITERAL),
						GML_SF0_PROFILE));
	}

	@Test
	public void everyNonEmptyXyGeometryCategoryUsesTheSchemaValidJenaFragmentMapping() throws Exception {
		Map<String, String> geometryCases = new LinkedHashMap<>();
		geometryCases.put("Point", "POINT(1 2)");
		geometryCases.put("MultiPoint", "MULTIPOINT((1 2),(3 4))");
		geometryCases.put("LineString", "LINESTRING(1 2,3 4)");
		geometryCases.put("MultiCurve", "MULTILINESTRING((1 2,3 4),(5 6,7 8))");
		geometryCases.put("Polygon", "POLYGON((0 0,2 0,2 2,0 0))");
		geometryCases.put("MultiSurface", "MULTIPOLYGON(((0 0,2 0,2 2,0 0)))");
		geometryCases.put("MultiGeometry",
				"GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(3 4,5 6))");

		for (Map.Entry<String, String> geometryCase : geometryCases.entrySet()) {
			Literal source = VALUE_FACTORY.createLiteral(geometryCase.getValue(), GeoConstants.GEO_WKT_LITERAL);
			Literal result = evaluate(source, GML_SF0_PROFILE);
			Document document = parseXml(result.stringValue());

			assertEquals(geometryCase.getKey(), document.getDocumentElement().getLocalName());
			assertEquals(GML_NAMESPACE,
					document.getDocumentElement().getNamespaceURI());
			gmlSchema.newValidator().validate(new StreamSource(new StringReader(
					withDocumentGeometryIds(document))));
		}
	}

	@Test
	public void wktGmlAndGeoJsonInputsRetainTheirSourceCrsAndAxisSemantics() throws Exception {
		List<Literal> sources = List.of(
				VALUE_FACTORY.createLiteral(
						"<" + EPSG_4326 + "> LINESTRING(50 10,51 11)", GeoConstants.GEO_WKT_LITERAL),
				VALUE_FACTORY.createLiteral(
						"<gml:Point xmlns:gml=\"http://www.opengis.net/gml/3.2\" srsName=\""
								+ EPSG_32634 + "\"><gml:pos>799997.80 4589779.63</gml:pos></gml:Point>",
						GeoConstants.GEO_GML_LITERAL),
				VALUE_FACTORY.createLiteral(
						"{\"type\":\"Point\",\"coordinates\":[1,2]}", GeoConstants.GEO_JSON_LITERAL));

		for (Literal source : sources) {
			SourceGeometryLiteral expected = JenaGeometryAdapter.toSourceGeometryLiteral(source);
			Literal result = evaluate(source, GML_SF0_PROFILE);
			SourceGeometryLiteral actual = JenaGeometryAdapter.toSourceGeometryLiteral(result);

			assertEquals(expected.effectiveCrsUri(), actual.effectiveCrsUri());
			assertTrue(expected.asGeometryWrapper().getParsingGeometry()
					.equalsExact(actual.asGeometryWrapper().getParsingGeometry()));
			assertTrue(expected.asGeometryWrapper().getXYGeometry()
					.equalsExact(actual.asGeometryWrapper().getXYGeometry()));
		}

		Literal epsg4326Result = evaluate(sources.get(0), GML_SF0_PROFILE);
		assertTrue(epsg4326Result.stringValue().contains("srsName=\"" + EPSG_4326 + "\""));
		assertTrue(epsg4326Result.stringValue().contains("<gml:posList>50 10 51 11</gml:posList>"));
		assertEquals(CRS84, JenaGeometryAdapter.toSourceGeometryLiteral(
				evaluate(sources.get(2), GML_SF0_PROFILE)).effectiveCrsUri());
	}

	@Test
	public void everyEmptySourceProducesTheReusableZeroLengthGmlForm() throws Exception {
		List<Literal> sources = List.of(
				VALUE_FACTORY.createLiteral("POINT EMPTY", GeoConstants.GEO_WKT_LITERAL),
				VALUE_FACTORY.createLiteral("MULTIPOINT EMPTY", GeoConstants.GEO_WKT_LITERAL),
				VALUE_FACTORY.createLiteral("LINESTRING EMPTY", GeoConstants.GEO_WKT_LITERAL),
				VALUE_FACTORY.createLiteral("MULTILINESTRING EMPTY", GeoConstants.GEO_WKT_LITERAL),
				VALUE_FACTORY.createLiteral("POLYGON EMPTY", GeoConstants.GEO_WKT_LITERAL),
				VALUE_FACTORY.createLiteral("MULTIPOLYGON EMPTY", GeoConstants.GEO_WKT_LITERAL),
				VALUE_FACTORY.createLiteral("GEOMETRYCOLLECTION EMPTY", GeoConstants.GEO_WKT_LITERAL),
				VALUE_FACTORY.createLiteral("", GeoConstants.GEO_GML_LITERAL),
				VALUE_FACTORY.createLiteral("", GeoConstants.GEO_JSON_LITERAL),
				VALUE_FACTORY.createLiteral(
						"{\"type\":\"Polygon\",\"coordinates\":[]}", GeoConstants.GEO_JSON_LITERAL));

		for (Literal source : sources) {
			Literal result = evaluate(source, GML_SF0_PROFILE);

			assertEquals(GeoConstants.GEO_GML_LITERAL, result.getDatatype());
			assertEquals("", result.stringValue());
			assertEquals(VALUE_FACTORY.createLiteral(true), evaluateFunction(GeoConstants.GEO_IS_EMPTY, result));
		}
	}

	@Test
	public void geometryAndCrsErrorsRemainExpressionErrors() {
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(VALUE_FACTORY.createIRI("http://example.com/geometry"), GML_SF0_PROFILE));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(VALUE_FACTORY.createLiteral("not geometry", GeoConstants.GEO_WKT_LITERAL),
						GML_SF0_PROFILE));
		assertThrows(ValueExprEvaluationException.class,
				() -> evaluate(VALUE_FACTORY.createLiteral(
						"<http://example.com/crs/unknown> POINT(1 2)", GeoConstants.GEO_WKT_LITERAL),
						GML_SF0_PROFILE));
	}

	private static Literal evaluate(Value... args) throws ValueExprEvaluationException {
		return (Literal) evaluateFunction(AS_GML, args);
	}

	private static Value evaluateFunction(IRI functionUri, Value... args) throws ValueExprEvaluationException {
		Function function = FunctionRegistry.getInstance().get(functionUri.stringValue())
				.orElseThrow(() -> new AssertionError("Function not registered: " + functionUri));
		return function.evaluate(TRIPLE_SOURCE, args);
	}

	private static Document parseXml(String lexicalForm) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		return factory.newDocumentBuilder().parse(
				new org.xml.sax.InputSource(new StringReader(lexicalForm)));
	}

	private static String withDocumentGeometryIds(Document document) throws Exception {
		NodeList elements = document.getElementsByTagNameNS(GML_NAMESPACE, "*");
		int geometryIndex = 0;
		for (int index = 0; index < elements.getLength(); index++) {
			Element element = (Element) elements.item(index);
			if (GML_GEOMETRY_ELEMENTS.contains(element.getLocalName())) {
				element.setAttributeNS(GML_NAMESPACE, "gml:id", "geometry-" + geometryIndex++);
			}
		}
		TransformerFactory factory = TransformerFactory.newInstance();
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
		Transformer transformer = factory.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		StringWriter output = new StringWriter();
		transformer.transform(new DOMSource(document), new StreamResult(output));
		return output.toString();
	}
}
