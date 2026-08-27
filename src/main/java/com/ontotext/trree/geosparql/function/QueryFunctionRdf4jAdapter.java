package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.GeoSparqlUnits;
import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.function.Function;

import java.math.BigInteger;
import java.util.Map;

final class QueryFunctionRdf4jAdapter implements Function {
	private static final Map<String, String> COMPATIBILITY_UNIT_TO_JENA = Map.ofEntries(
			Map.entry(GeoSparqlUnits.URI_CENTIMETRE.stringValue(), Unit_URI.CENTIMETRE_URL),
			Map.entry(GeoSparqlUnits.URI_KILOMETRE.stringValue(), Unit_URI.KILOMETRE_URL),
			Map.entry(GeoSparqlUnits.URI_MILLIMETRE.stringValue(), Unit_URI.MILLIMETRE_URL),
			Map.entry(GeoSparqlUnits.URI_METRE.stringValue(), Unit_URI.METRE_URL),
			Map.entry(GeoSparqlUnits.URI_FOOT.stringValue(), Unit_URI.FOOT_URL),
			Map.entry(GeoSparqlUnits.URI_US_SURVEY_FOOT.stringValue(), Unit_URI.US_SURVEY_FOOT_URL),
			Map.entry(GeoSparqlUnits.URI_INCH.stringValue(), Unit_URI.INCH_URL),
			Map.entry(GeoSparqlUnits.URI_MILE.stringValue(), Unit_URI.MILE_URL),
			Map.entry(GeoSparqlUnits.URI_NAUTICAL_MILE.stringValue(), Unit_URI.NAUTICAL_MILE_URL),
			Map.entry(GeoSparqlUnits.URI_YARD.stringValue(), Unit_URI.YARD_URL));
	private final QueryFunctionManifest.Entry entry;
	private final Function compatibilityOverload;

	QueryFunctionRdf4jAdapter(QueryFunctionManifest.Entry entry) {
		this(entry, null);
	}

	QueryFunctionRdf4jAdapter(QueryFunctionManifest.Entry entry, Function compatibilityOverload) {
		this.entry = entry;
		this.compatibilityOverload = compatibilityOverload;
	}

	@Override
	public String getURI() {
		return entry.uri();
	}

	@Override
	public Value evaluate(ValueFactory valueFactory, Value... args) throws ValueExprEvaluationException {
		try {
			if (args.length != entry.mandatoryArity() && compatibilityOverload != null) {
				return compatibilityOverload.evaluate(valueFactory, args);
			}
			requireMandatoryArity(args);
			return evaluateProvider(valueFactory, args);
		} catch (ValueExprEvaluationException e) {
			throw e;
		} catch (Exception e) {
			throw new ValueExprEvaluationException(e);
		}
	}

	private Value evaluateProvider(ValueFactory valueFactory, Value[] args) throws Exception {
		return switch (entry.provider()) {
			case QueryFunctionManifest.BinaryGeometryDoubleProvider provider -> {
				double result = provider.calculation().apply(
						geometryArgument(args[0]), geometryArgument(args[1]));
				yield valueFactory.createLiteral(result);
			}
			case QueryFunctionManifest.BinaryGeometryUnitDoubleProvider provider -> {
				double result = provider.calculation().apply(
						geometryArgument(args[0]), geometryArgument(args[1]), unitUri(args[2]));
				yield valueFactory.createLiteral(result);
			}
			case QueryFunctionManifest.GeometryMemberProvider provider -> {
				SourceGeometryLiteral source = JenaGeometryAdapter.toSourceGeometryLiteral(args[0], true);
				GeometryWrapper result = provider.calculation().apply(
						source.asGeometryWrapper(), memberIndex(args[1]));
				yield JenaGeometryAdapter.toQueryGeometryLiteral(valueFactory, result, source.datatype());
			}
			case QueryFunctionManifest.UnaryGeometryAnyUriProvider provider -> {
				String result = provider.calculation().apply(geometryArgument(args[0]));
				yield valueFactory.createLiteral(result, XSD.ANYURI);
			}
			case QueryFunctionManifest.UnaryGeometryBooleanProvider provider -> {
				boolean result = provider.calculation().test(geometryArgument(args[0]));
				yield valueFactory.createLiteral(result);
			}
			case QueryFunctionManifest.UnaryGeometryDoubleProvider provider -> {
				SourceGeometryLiteral source = sourceGeometryArgument(args[0]);
				GeometryWrapper result = provider.calculation().apply(
						source.asGeometryWrapper(), finiteNumeric(args[1]));
				yield JenaGeometryAdapter.toQueryGeometryLiteral(valueFactory, result, source.datatype());
			}
			case QueryFunctionManifest.UnaryGeometryDoubleUnitProvider provider -> {
				SourceGeometryLiteral source = sourceGeometryArgument(args[0]);
				GeometryWrapper result = provider.calculation().apply(
						source.asGeometryWrapper(), finiteNumeric(args[1]), unitUri(args[2]));
				yield JenaGeometryAdapter.toQueryGeometryLiteral(valueFactory, result, source.datatype());
			}
			case QueryFunctionManifest.UnaryGeometryIntegerProvider provider -> {
				int result = provider.calculation().applyAsInt(geometryArgument(args[0]));
				yield valueFactory.createLiteral(BigInteger.valueOf(result));
			}
		};
	}

	private GeometryWrapper geometryArgument(Value value) {
		return sourceGeometryArgument(value).asGeometryWrapper();
	}

	private SourceGeometryLiteral sourceGeometryArgument(Value value) {
		return JenaGeometryAdapter.toSourceGeometryLiteral(value, true);
	}

	private double finiteNumeric(Value value) {
		if (!(value instanceof Literal literal)
				|| !XMLDatatypeUtil.isNumericDatatype(literal.getDatatype())
				|| !XMLDatatypeUtil.isValidValue(literal.getLabel(), literal.getDatatype())) {
			throw new IllegalArgumentException("Expected a numeric radius, found: " + value);
		}
		double numericValue = literal.doubleValue();
		if (!Double.isFinite(numericValue)) {
			throw new IllegalArgumentException("Expected a finite radius, found: " + value);
		}
		return numericValue;
	}

	private String unitUri(Value value) {
		String uri;
		if (value instanceof IRI) {
			uri = value.stringValue();
		} else if (value instanceof Literal literal
				&& literal.getLanguage().isEmpty()
				&& XSD.ANYURI.equals(literal.getDatatype())) {
			uri = literal.stringValue();
		} else {
			throw new IllegalArgumentException("Expected a unit IRI or xsd:anyURI literal, found: " + value);
		}
		return COMPATIBILITY_UNIT_TO_JENA.getOrDefault(uri, uri);
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
