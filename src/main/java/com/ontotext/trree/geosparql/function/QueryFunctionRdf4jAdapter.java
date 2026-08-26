package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.function.Function;

import java.math.BigInteger;

final class QueryFunctionRdf4jAdapter implements Function {
	private final QueryFunctionManifest.Entry entry;

	QueryFunctionRdf4jAdapter(QueryFunctionManifest.Entry entry) {
		this.entry = entry;
	}

	@Override
	public String getURI() {
		return entry.uri();
	}

	@Override
	public Value evaluate(ValueFactory valueFactory, Value... args) throws ValueExprEvaluationException {
		try {
			requireMandatoryArity(args);
			return evaluateProvider(valueFactory, args);
		} catch (ValueExprEvaluationException e) {
			throw e;
		} catch (Exception e) {
			throw new ValueExprEvaluationException(e);
		}
	}

	private Value evaluateProvider(ValueFactory valueFactory, Value[] args) {
		return switch (entry.provider()) {
			case QueryFunctionManifest.GeometryMemberProvider provider -> {
				SourceGeometryLiteral source = JenaGeometryAdapter.toSourceGeometryLiteral(args[0], true);
				GeometryWrapper result = provider.calculation().apply(
						source.asGeometryWrapper(), memberIndex(args[1]));
				yield JenaGeometryAdapter.toQueryGeometryLiteral(valueFactory, result, source.datatype());
			}
			case QueryFunctionManifest.UnaryGeometryIntegerProvider provider -> {
				GeometryWrapper geometry = JenaGeometryAdapter.toSourceGeometryLiteral(args[0], true)
						.asGeometryWrapper();
				int result = provider.calculation().applyAsInt(geometry);
				yield valueFactory.createLiteral(BigInteger.valueOf(result));
			}
		};
	}

	private int memberIndex(Value value) {
		if (!(value instanceof Literal literal)
				|| !XMLDatatypeUtil.isNumericDatatype(literal.getDatatype())
				|| !XMLDatatypeUtil.isValidValue(literal.getLabel(), literal.getDatatype())) {
			throw new IllegalArgumentException("Expected a numeric geometry member index, found: " + value);
		}
		if (XSD.FLOAT.equals(literal.getDatatype())) {
			return floatingPointMemberIndex(literal.floatValue());
		}
		if (XSD.DOUBLE.equals(literal.getDatatype())) {
			return floatingPointMemberIndex(literal.doubleValue());
		}
		return literal.decimalValue().toBigIntegerExact().intValueExact();
	}

	private int floatingPointMemberIndex(double index) {
		if (!Double.isFinite(index) || index != Math.rint(index)
				|| index < Integer.MIN_VALUE || index > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Expected a losslessly integral geometry member index, found: " + index);
		}
		return (int) index;
	}

	private void requireMandatoryArity(Value[] args) throws ValueExprEvaluationException {
		if (args.length != entry.mandatoryArity()) {
			throw new ValueExprEvaluationException(entry.uri() + " expects "
					+ entry.mandatoryArity() + " argument(s), found " + args.length);
		}
	}
}
