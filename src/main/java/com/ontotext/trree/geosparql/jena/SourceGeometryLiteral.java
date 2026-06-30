package com.ontotext.trree.geosparql.jena;

import com.useekm.indexing.GeoConstants;
import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.GeometryDatatype;
import org.apache.jena.geosparql.implementation.datatype.GMLDatatype;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CRS-preserving geometry literal used for exact Jena evaluation.
 */
public final class SourceGeometryLiteral {
	private static final Pattern GML_SRS_NAME =
			Pattern.compile("\\bsrsName\\s*=\\s*['\"]([^'\"]+)['\"]");
	private static final String RDF_XML_LITERAL = "http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral";
	private static final String GML_LEGACY_NAMESPACE = "http://www.opengis.net/gml";
	private static final String GML_32_NAMESPACE = "http://www.opengis.net/gml/3.2";

	private final String lexicalForm;
	private final String jenaLexicalForm;
	private final IRI datatype;
	private final IRI jenaDatatype;
	private final String explicitCrsUri;
	private transient GeometryWrapper geometryWrapper;

	private SourceGeometryLiteral(String lexicalForm, IRI datatype, IRI jenaDatatype, String explicitCrsUri) {
		this.lexicalForm = lexicalForm;
		this.jenaLexicalForm = toJenaLexicalForm(lexicalForm, jenaDatatype);
		this.datatype = datatype;
		this.jenaDatatype = jenaDatatype;
		this.explicitCrsUri = explicitCrsUri;
	}

	public static SourceGeometryLiteral fromValue(Value value, boolean acceptNoType) {
		if (!(value instanceof Literal)) {
			throw new JenaGeoSparqlException("Expected a geometry literal, found: " + value);
		}
		return fromLiteral((Literal) value, acceptNoType ? GeoConstants.GEO_WKT_LITERAL : null);
	}

	public static SourceGeometryLiteral fromLiteral(Literal literal) {
		return fromLiteral(literal, null);
	}

	public static SourceGeometryLiteral fromLiteral(Literal literal, IRI fallbackDatatype) {
		IRI datatype = literal.getDatatype();
		if (shouldUseFallbackDatatype(datatype, fallbackDatatype)) {
			datatype = fallbackDatatype;
		}

		IRI jenaDatatype = toJenaGeometryDatatype(datatype);
		String lexicalForm = literal.stringValue();
		String explicitCrsUri = extractExplicitCrs(lexicalForm, jenaDatatype);
		return new SourceGeometryLiteral(lexicalForm, datatype, jenaDatatype, explicitCrsUri);
	}

	public static SourceGeometryLiteral fromWkt(String lexicalForm) {
		return new SourceGeometryLiteral(lexicalForm, GeoConstants.GEO_WKT_LITERAL, GeoConstants.GEO_WKT_LITERAL,
				extractWktCrs(lexicalForm));
	}

	public String lexicalForm() {
		return lexicalForm;
	}

	public IRI datatype() {
		return datatype;
	}

	public IRI jenaDatatype() {
		return jenaDatatype;
	}

	public Optional<String> explicitCrsUri() {
		return Optional.ofNullable(explicitCrsUri);
	}

	public Node asJenaNode() {
		return NodeFactory.createLiteral(jenaLexicalForm, null, GeometryDatatype.get(jenaDatatype.stringValue()));
	}

	public GeometryWrapper asGeometryWrapper() {
		if (geometryWrapper == null) {
			try {
				geometryWrapper = GeometryWrapper.extract(jenaLexicalForm, jenaDatatype.stringValue());
			} catch (DatatypeFormatException e) {
				throw new JenaGeoSparqlException("Invalid GeoSPARQL geometry literal: " + e.getMessage(), e);
			} catch (ServiceConfigurationError e) {
				throw new JenaGeoSparqlException("Invalid GeoSPARQL geometry literal: " + e.getMessage(), e);
			} catch (RuntimeException e) {
				throw new JenaGeoSparqlException("Invalid GeoSPARQL geometry literal: " + e.getMessage(), e);
			}

			if (!geometryWrapper.isSRSRecognised()) {
				throw new JenaGeoSparqlException(unsupportedCrsMessage(geometryWrapper.getSrsURI()), null, true);
			}
			if (geometryWrapper.getSrsInfo().isGeographic()
					&& !geometryWrapper.isEmpty()
					&& !geometryWrapper.getSrsInfo().getDomainEnvelope()
							.contains(geometryWrapper.getXYGeometry().getEnvelopeInternal())) {
				throw new JenaGeoSparqlException("Geometry coordinates are outside the CRS domain for "
						+ geometryWrapper.getSrsURI());
			}
		}
		return geometryWrapper;
	}

	static String unsupportedCrsMessage(String crsUri) {
		return "Unsupported CRS for GeoSPARQL geometry literal: " + crsUri
				+ ". Configure Apache SIS CRS data, for example SIS_DATA, or use a supported CRS.";
	}

	private static IRI toJenaGeometryDatatype(IRI datatype) {
		if (GeoConstants.GEO_GML_LITERAL.equals(datatype)) {
			return GeoConstants.GEO_GML_LITERAL;
		}
		if (GeoConstants.GEO_WKT_LITERAL.equals(datatype)
				|| GeoConstants.XMLSCHEMA_OGC_WKT.equals(datatype)
				|| GeoConstants.XMLSCHEMA_SPATIAL_TEXT.equals(datatype)
				|| XMLSchema.STRING.equals(datatype)) {
			return GeoConstants.GEO_WKT_LITERAL;
		}
		throw new JenaGeoSparqlException("Unsupported GeoSPARQL geometry datatype: " + datatype);
	}

	private static boolean shouldUseFallbackDatatype(IRI datatype, IRI fallbackDatatype) {
		if (fallbackDatatype == null) {
			return false;
		}
		if (isPlainString(datatype)) {
			return true;
		}
		return GeoConstants.GEO_GML_LITERAL.equals(fallbackDatatype)
				&& datatype != null
				&& RDF_XML_LITERAL.equals(datatype.stringValue());
	}

	private static boolean isPlainString(IRI datatype) {
		return datatype == null || XMLSchema.STRING.equals(datatype);
	}

	private static String extractExplicitCrs(String lexicalForm, IRI jenaDatatype) {
		if (GeoConstants.GEO_GML_LITERAL.equals(jenaDatatype)) {
			Matcher matcher = GML_SRS_NAME.matcher(lexicalForm);
			return matcher.find() ? matcher.group(1) : null;
		}
		return extractWktCrs(lexicalForm);
	}

	private static String extractWktCrs(String lexicalForm) {
		int start = lexicalForm.indexOf('<');
		if (start < 0) {
			return null;
		}
		for (int i = 0; i < start; i++) {
			if (lexicalForm.charAt(i) > 32) {
				return null;
			}
		}
		int end = lexicalForm.indexOf('>', start + 1);
		if (end < 0) {
			throw new JenaGeoSparqlException("CRS URI in WKT literal is incomplete: " + lexicalForm);
		}
		return lexicalForm.substring(start + 1, end);
	}

	private static String toJenaLexicalForm(String lexicalForm, IRI jenaDatatype) {
		if (!GeoConstants.GEO_GML_LITERAL.equals(jenaDatatype)) {
			return lexicalForm;
		}
		return lexicalForm
				.replace("=\"" + GML_LEGACY_NAMESPACE + "\"", "=\"" + GML_32_NAMESPACE + "\"")
				.replace("='" + GML_LEGACY_NAMESPACE + "'", "='" + GML_32_NAMESPACE + "'");
	}

	public static IRI wktDatatype() {
		return SimpleValueFactory.getInstance().createIRI(WKTDatatype.URI);
	}

	public static IRI gmlDatatype() {
		return SimpleValueFactory.getInstance().createIRI(GMLDatatype.URI);
	}
}
