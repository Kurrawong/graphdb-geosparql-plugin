package com.ontotext.trree.geosparql.jena.query;

import static org.junit.Assert.assertEquals;

import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.apache.jena.geosparql.implementation.datatype.GMLDatatype;
import org.apache.jena.geosparql.implementation.datatype.WKTDatatype;
import org.junit.Test;

public class GeometryTypeSerializationTest {
	private static final String GML = "http://www.opengis.net/ont/gml#";
	private static final String SF = "http://www.opengis.net/ont/sf#";

	@Test
	public void copyRetainsSourceSubtypeWithDefaultXmlNamespace() {
		GeometryWrapper source = curve();
		assertEquals(GML + "Curve", type(source));
		assertEquals(GML + "Curve", type(new GeometryWrapper(source)));
	}

	@Test
	public void conversionToWktUsesTheConvertedTypeWithoutChangingSource() {
		GeometryWrapper source = curve();
		GeometryWrapper converted = GeometryWrapper.extract(source.asLiteral(WKTDatatype.URI));
		assertEquals(SF + "LineString", type(converted));
		assertEquals(GML + "Curve", type(source));
	}

	@Test
	public void constructedGmlUsesWriterElementNames() {
		String[][] cases = {
			{ "POINT (1 2)", "Point" },
			{ "LINESTRING (0 0, 1 1)", "LineString" },
			{ "MULTILINESTRING ((0 0, 1 1))", "MultiCurve" },
			{ "MULTIPOLYGON (((0 0, 1 0, 0 1, 0 0)))", "MultiSurface" },
			{ "GEOMETRYCOLLECTION (POINT (1 2))", "MultiGeometry" },
			{ "MULTIPOLYGON EMPTY", "MultiSurface" }
		};
		for (String[] example : cases) {
			GeometryWrapper wkt = GeometryWrapper.extract(example[0], WKTDatatype.URI);
			GeometryWrapper constructed = new GeometryWrapper(wkt.getParsingGeometry(), wkt.getSrsURI(),
															   GMLDatatype.URI, wkt.getDimensionInfo());
			assertEquals(example[0], GML + example[1], type(constructed));
			assertEquals(example[0], GML + example[1], type(GeometryWrapper.extract(constructed.asLiteral())));
		}
	}

	@Test
	public void emptyGmlTextUsesJenasPointInterpretation() {
		assertEquals(GML + "Point", type(GeometryWrapper.extract("", GMLDatatype.URI)));
	}

	private static GeometryWrapper curve() {
		return GeometryWrapper.extract("""
			<Curve xmlns="http://www.opengis.net/gml/3.2"
				   srsName="http://www.opengis.net/def/crs/EPSG/0/27700">
			  <segments><LineStringSegment><posList>0 0 10 10 20 0</posList></LineStringSegment></segments>
			</Curve>
			""", GMLDatatype.URI);
	}

	private static String type(GeometryWrapper geometry) {
		return GeometryMetadata.geometryTypeUri(geometry);
	}
}
