package com.ontotext.trree.geosparql.jena.query;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.SRSInfo;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.apache.sis.referencing.CRS;
import org.junit.Test;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import static org.junit.Assert.assertThrows;

public class DirectionalPointCoordinatesTest {
	@Test
	public void geocentricCrsHasNoEastwardOrNorthwardAxis() throws Exception {
		GeometryWrapper source = GeometryWrapper.extract("POINT(1 2)", WKTDatatype.URI);
		CoordinateReferenceSystem geocentricCrs = CRS.forCode("EPSG:4978");
		GeometryWrapper point = new GeometryWrapper(source) {
			@Override
			public SRSInfo getSrsInfo() {
				return new SRSInfo(SRS_URI.DEFAULT_WKT_CRS84) {
					@Override
					public CoordinateReferenceSystem getCrs() {
						return geocentricCrs;
					}
				};
			}
		};

		assertThrows(IllegalArgumentException.class,
				() -> DirectionalPointCoordinates.easting(point));
		assertThrows(IllegalArgumentException.class,
				() -> DirectionalPointCoordinates.northing(point));
	}
}
