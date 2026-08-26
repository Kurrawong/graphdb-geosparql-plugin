package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
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
			case QueryFunctionManifest.UnaryGeometryIntegerProvider provider -> {
				GeometryWrapper geometry = JenaGeometryAdapter.toSourceGeometryLiteral(args[0], true)
						.asGeometryWrapper();
				int result = provider.calculation().applyAsInt(geometry);
				yield valueFactory.createLiteral(BigInteger.valueOf(result));
			}
		};
	}

	private void requireMandatoryArity(Value[] args) throws ValueExprEvaluationException {
		if (args.length != entry.mandatoryArity()) {
			throw new ValueExprEvaluationException(entry.uri() + " expects "
					+ entry.mandatoryArity() + " argument(s), found " + args.length);
		}
	}
}
