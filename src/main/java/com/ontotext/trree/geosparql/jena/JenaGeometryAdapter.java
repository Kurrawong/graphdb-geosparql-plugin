package com.ontotext.trree.geosparql.jena;

import org.apache.jena.geosparql.implementation.datatype.GeometryDatatype;
import org.apache.jena.geosparql.implementation.registry.SRSRegistry;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;

/**
 * Thin RDF4J-to-Jena geometry adapter.
 */
public final class JenaGeometryAdapter {
	private JenaGeometryAdapter() {
	}

	public static void initialize() {
		SRSRegistry.setupDefaultSRS();
		GeometryDatatype.registerDatatypes();
	}

	public static ExactGeometry toExactGeometry(Literal literal) {
		initialize();
		return ExactGeometry.fromLiteral(literal);
	}

	public static ExactGeometry toExactGeometry(Literal literal, IRI fallbackDatatype) {
		initialize();
		return ExactGeometry.fromLiteral(literal, fallbackDatatype);
	}

	public static ExactGeometry toExactGeometry(Value value, boolean acceptNoType) {
		initialize();
		return ExactGeometry.fromValue(value, acceptNoType);
	}

	public static IndexGeometry toIndexGeometry(ExactGeometry exactGeometry) {
		initialize();
		return IndexGeometry.fromExactGeometry(exactGeometry);
	}

	public static Literal toRdf4jLiteral(ValueFactory valueFactory, org.apache.jena.geosparql.implementation.GeometryWrapper wrapper,
										 IRI datatype) {
		org.apache.jena.rdf.model.Literal literal = wrapper.asLiteral(ExactGeometry.fromLiteral(
				valueFactory.createLiteral(wrapper.getLexicalForm(), datatype)).jenaDatatype().stringValue());
		return valueFactory.createLiteral(literal.getLexicalForm(), datatype);
	}
}
