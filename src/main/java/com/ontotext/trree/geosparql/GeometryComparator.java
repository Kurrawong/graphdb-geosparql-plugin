package com.ontotext.trree.geosparql;

import com.ontotext.trree.geosparql.jena.ExactGeometry;
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
        return function.evaluate(ExactGeometry.fromWkt(g1.toText()), ExactGeometry.fromWkt(g2.toText()));
    }
}
