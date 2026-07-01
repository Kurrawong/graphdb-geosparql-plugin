package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compatibility rules for geometry literals accepted by the pre-Jena plugin and existing fixture data.
 *
 * SourceGeometryLiteral keeps the original RDF4J lexical form/datatype for storage and result fidelity, while this
 * helper derives the datatype and lexical form that Apache Jena's GeoSPARQL parser can consume.
 */
final class GeometryLiteralCompatibility {
	private static final Pattern GML_SRS_NAME =
			Pattern.compile("\\bsrsName\\s*=\\s*['\"]([^'\"]+)['\"]");
	private static final String RDF_XML_LITERAL = "http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral";
	private static final String GML_LEGACY_NAMESPACE = "http://www.opengis.net/gml";
	private static final String GML_32_NAMESPACE = "http://www.opengis.net/gml/3.2";

	private GeometryLiteralCompatibility() {
	}

	static IRI datatypeOrFallback(IRI datatype, IRI fallbackDatatype) {
		return shouldUseFallbackDatatype(datatype, fallbackDatatype) ? fallbackDatatype : datatype;
	}

	/**
	 * Extracts CRS metadata from the source lexical form before any Jena-only GML namespace normalization.
	 */
	static String extractExplicitCrs(String lexicalForm, IRI jenaDatatype) {
		if (GeoConstants.GEO_GML_LITERAL.equals(jenaDatatype)) {
			Matcher matcher = GML_SRS_NAME.matcher(lexicalForm);
			return matcher.find() ? matcher.group(1) : null;
		}
		return extractWktCrs(lexicalForm);
	}

	/**
	 * Jena's GML parser expects the GML 3.2 namespace, but existing data can use the older GML namespace.
	 */
	static String toJenaLexicalForm(String lexicalForm, IRI jenaDatatype) {
		if (!GeoConstants.GEO_GML_LITERAL.equals(jenaDatatype)) {
			return lexicalForm;
		}
		return lexicalForm
				.replace("=\"" + GML_LEGACY_NAMESPACE + "\"", "=\"" + GML_32_NAMESPACE + "\"")
				.replace("='" + GML_LEGACY_NAMESPACE + "'", "='" + GML_32_NAMESPACE + "'");
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
}
