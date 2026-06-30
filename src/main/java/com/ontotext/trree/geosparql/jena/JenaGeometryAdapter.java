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

	public static Literal toRdf4jLiteral(ValueFactory valueFactory, org.apache.jena.geosparql.implementation.GeometryWrapper wrapper,
										 IRI datatype) {
		org.apache.jena.rdf.model.Literal literal = wrapper.asLiteral(SourceGeometryLiteral.fromLiteral(
				valueFactory.createLiteral(wrapper.getLexicalForm(), datatype)).jenaDatatype().stringValue());
		return valueFactory.createLiteral(literal.getLexicalForm(), datatype);
	}
}
