package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.Crs84ResultDimensionPolicy;
import com.ontotext.trree.geosparql.jena.JenaGeoSparqlException;
import com.ontotext.trree.geosparql.jena.JenaGeometryAdapter;
import com.ontotext.trree.geosparql.jena.query.GeometryAggregate;
import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.parser.sparql.aggregate.AggregateCollector;
import org.eclipse.rdf4j.query.parser.sparql.aggregate.AggregateFunction;
import org.eclipse.rdf4j.query.parser.sparql.aggregate.AggregateFunctionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * RDF4J custom aggregate for one GeoSPARQL spatial aggregate function. A group's result is unbound when the group
 * has no geometries or when any value in it is not a valid geometry literal.
 */
final class SpatialAggregateFactory implements AggregateFunctionFactory {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();

	private final String iri;
	private final Function<List<GeometryWrapper>, GeometryWrapper> calculation;

	private SpatialAggregateFactory(String iri, Function<List<GeometryWrapper>, GeometryWrapper> calculation) {
		this.iri = iri;
		this.calculation = calculation;
	}

	static List<SpatialAggregateFactory> all() {
		return List.of(
				new SpatialAggregateFactory(GeoConstants.GEOF_AGG_BOUNDING_BOX.stringValue(),
						GeometryAggregate::boundingBox),
				new SpatialAggregateFactory(GeoConstants.GEOF_AGG_BOUNDING_CIRCLE.stringValue(),
						GeometryAggregate::boundingCircle),
				new SpatialAggregateFactory(GeoConstants.GEOF_AGG_CENTROID.stringValue(),
						GeometryAggregate::centroid),
				new SpatialAggregateFactory(GeoConstants.GEOF_AGG_COLLECT.stringValue(),
						GeometryAggregate::collect),
				new SpatialAggregateFactory(GeoConstants.GEOF_AGG_CONCAVE_HULL.stringValue(),
						geometries -> GeometryAggregate.concaveHull(geometries, 0.0)),
				new SpatialAggregateFactory(GeoConstants.GEOF_AGG_CONVEX_HULL.stringValue(),
						GeometryAggregate::convexHull),
				new SpatialAggregateFactory(GeoConstants.GEOF_AGG_UNION.stringValue(),
						GeometryAggregate::union));
	}

	@Override
	public String getIri() {
		return iri;
	}

	@Override
	public AggregateFunction<GeometryCollector, Value> buildFunction(Function<BindingSet, Value> evaluationStep) {
		return new AggregateFunction<>(evaluationStep) {
			@Override
			public void processAggregate(BindingSet bindings, Predicate<Value> distinctValue,
					GeometryCollector collector) {
				if (collector.failed) {
					return;
				}
				try {
					Value value = evaluate(bindings);
					if (value == null) {
						collector.failed = true;
					} else if (distinctValue.test(value)) {
						collector.geometries.add(
								JenaGeometryAdapter.toSourceGeometryLiteral(value, true).asGeometryWrapper());
					}
				} catch (JenaGeoSparqlException | QueryEvaluationException e) {
					collector.failed = true;
				}
			}
		};
	}

	@Override
	public AggregateCollector getCollector() {
		return new GeometryCollector();
	}

	private final class GeometryCollector implements AggregateCollector {
		private final List<GeometryWrapper> geometries = new ArrayList<>();
		private boolean failed;

		@Override
		public Value getFinalValue() {
			if (failed || geometries.isEmpty()) {
				return null;
			}
			try {
				GeometryWrapper result = calculation.apply(geometries);
				return JenaGeometryAdapter.toQueryGeometryLiteral(VALUE_FACTORY, result, result,
						VALUE_FACTORY.createIRI(result.getGeometryDatatypeURI()), Crs84ResultDimensionPolicy.XY_ONLY);
			} catch (JenaGeoSparqlException e) {
				return null;
			}
		}
	}
}
