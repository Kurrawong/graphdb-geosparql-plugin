package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.SourceGeometryLiteral;
import org.locationtech.jts.geom.Geometry;

/**
 * Created by avataar on 06.10.2016..
 */
class GeometryComparator {
    protected GeoSparqlFunction function;

    GeometryComparator(GeoSparqlFunction function) {
        this.function = function;
    }

    boolean evaluate(Geometry g1, Geometry g2) {
        return function.evaluate(SourceGeometryLiteral.fromWkt(g1.toText()), SourceGeometryLiteral.fromWkt(g2.toText()));
    }
}
