package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.datatype.GeometryDatatype;
import org.apache.jena.geosparql.implementation.registry.SRSRegistry;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.locationtech.jts.io.WKTWriter;

/**
 * Central RDF4J-to-Jena conversion entry point for geometry values.
 *
 * <p>The adapter initializes Jena's geometry registries, preserves RDF literal datatype and lexical form in
	 * {@link SourceGeometryLiteral}, and derives the CRS84 {@link IndexGeometry} value used by Lucene.
 * Exact relation semantics remain in the Jena evaluator rather than this conversion layer.
 */
public final class JenaGeometryAdapter {
	private JenaGeometryAdapter() {
	}

	public static void initialize() {
		JenaCalculationPrecision.configure();
		SRSRegistry.setupDefaultSRS();
		GeometryDatatype.registerDatatypes();
	}

	public static SourceGeometryLiteral toSourceGeometryLiteral(Literal literal) {
		initialize();
		return SourceGeometryLiteral.fromLiteral(literal);
	}

	public static SourceGeometryLiteral toSourceGeometryLiteral(Literal literal, IRI fallbackDatatype) {
		initialize();
		return SourceGeometryLiteral.fromLiteral(literal, fallbackDatatype);
	}

	public static SourceGeometryLiteral toSourceGeometryLiteral(Value value, boolean acceptNoType) {
		initialize();
		return SourceGeometryLiteral.fromValue(value, acceptNoType);
	}

	public static IndexGeometry toIndexGeometry(SourceGeometryLiteral sourceGeometryLiteral) {
		initialize();
		return IndexGeometry.fromSourceGeometryLiteral(sourceGeometryLiteral);
	}

	public static Literal toRdf4jLiteral(ValueFactory valueFactory,
			org.apache.jena.geosparql.implementation.GeometryWrapper wrapper, IRI datatype) {
		IRI jenaDatatype = SourceGeometryLiteral.fromLiteral(
				valueFactory.createLiteral(wrapper.getLexicalForm(), datatype)).jenaDatatype();
		if (GeoConstants.GEO_WKT_LITERAL.equals(jenaDatatype)) {
			String wkt = new WKTWriter().write(wrapper.getParsingGeometry());
			if (!SRS_URI.DEFAULT_WKT_CRS84.equals(wrapper.getSrsURI())) {
				wkt = "<" + wrapper.getSrsURI() + "> " + wkt;
			}
			return valueFactory.createLiteral(wkt, datatype);
		}
		org.apache.jena.rdf.model.Literal literal = wrapper.asLiteral(jenaDatatype.stringValue());
		return valueFactory.createLiteral(literal.getLexicalForm(), datatype);
	}
}
