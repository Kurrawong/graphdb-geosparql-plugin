package com.ontotext.trree.geosparql.jena;

import com.ontotext.trree.geosparql.vocabulary.GeoConstants;
import org.apache.jena.geosparql.implementation.vocabulary.SRS_URI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.apache.jena.geosparql.implementation.GeometryWrapper;
import org.junit.Test;
import org.locationtech.jts.geom.Point;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Verifies KML source geometry literals parsed through the JTS KML reader.
 */
public class KmlLiteralTest {
	private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();

	@Test
	public void kmlPointIsCrs84SourceGeometryLiteral() {
		SourceGeometryLiteral source = source(
				"<Point xmlns=\"http://www.opengis.net/kml/2.2\"><coordinates>-83.38,33.95</coordinates></Point>");
		Point point = (Point) source.asGeometryWrapper().getParsingGeometry();

		assertEquals(GeoConstants.GEO_KML_LITERAL, source.datatype());
		assertEquals(GeoConstants.GEO_KML_LITERAL, source.jenaDatatype());
		assertEquals(SRS_URI.DEFAULT_WKT_CRS84, source.effectiveCrsUri());
		assertEquals(-83.38, point.getX(), 0.0);
		assertEquals(33.95, point.getY(), 0.0);
		assertEquals(2, source.asGeometryWrapper().getCoordinateDimension());
	}

	@Test
	public void emptyLexicalFormIsEmptyPoint() {
		SourceGeometryLiteral source = source("");

		assertTrue(source.asGeometryWrapper().isEmpty());
		assertEquals("Point", source.asGeometryWrapper().getParsingGeometry().getGeometryType());
		assertEquals(2, source.asGeometryWrapper().getCoordinateDimension());
	}

	@Test
	public void documentTypeDeclarationIsRejected() {
		SourceGeometryLiteral source = source("<!DOCTYPE Point [<!ENTITY position \"1,2\">]>"
				+ "<Point><coordinates>&position;</coordinates></Point>");

		assertThrows(JenaGeoSparqlException.class, source::asGeometryWrapper);
	}

	@Test
	public void emptyMultiGeometryIsEmptyGeometryCollection() {
		SourceGeometryLiteral source = source("<MultiGeometry xmlns=\"http://www.opengis.net/kml/2.2\"/>");

		assertTrue(source.asGeometryWrapper().isEmpty());
		assertEquals("GeometryCollection", source.asGeometryWrapper().getParsingGeometry().getGeometryType());
	}

	@Test
	public void geometryIsThreeDimensionalOnlyWhenEveryTupleHasAltitude() {
		GeometryWrapper everyAltitude = source("<LineString><coordinates>1,2,10 3,4,20</coordinates></LineString>")
				.asGeometryWrapper();
		GeometryWrapper someAltitudes = source("<LineString><coordinates>1,2,10 3,4</coordinates></LineString>")
				.asGeometryWrapper();

		assertEquals(3, everyAltitude.getCoordinateDimension());
		assertEquals(20.0, everyAltitude.getParsingGeometry().getCoordinates()[1].getZ(), 0.0);
		assertEquals(2, someAltitudes.getCoordinateDimension());
		assertTrue(Double.isNaN(someAltitudes.getParsingGeometry().getCoordinates()[0].getZ()));
	}

	@Test
	public void coordinatesOutsideCrs84DomainOrNonFiniteAreRejected() {
		for (String coordinates : new String[] {"181,2", "1,91", "NaN,2", "1,Infinity"}) {
			SourceGeometryLiteral source = source("<Point><coordinates>" + coordinates + "</coordinates></Point>");

			assertThrows(coordinates, JenaGeoSparqlException.class, source::asGeometryWrapper);
		}
	}

	@Test
	public void nestedMultiGeometryIsRejected() {
		SourceGeometryLiteral source = source("<MultiGeometry><Point><coordinates>1,2</coordinates></Point>"
				+ "<MultiGeometry><Point><coordinates>3,4</coordinates></Point></MultiGeometry></MultiGeometry>");

		assertThrows(JenaGeoSparqlException.class, source::asGeometryWrapper);
	}

	@Test
	public void infiniteOrdinatesAreRejected() {
		for (String coordinates : new String[] {"1,2,Infinity 3,4,5", "1,2,-Infinity 3,4,5", "1,2,Infinity 3,4",
				"1,2 3,4,-Infinity"}) {
			SourceGeometryLiteral source = source("<LineString><coordinates>" + coordinates
					+ "</coordinates></LineString>");

			assertThrows(coordinates, JenaGeoSparqlException.class, source::asGeometryWrapper);
		}
	}

	@Test
	public void notANumberHorizontalOrdinateInAnyTupleIsRejected() {
		for (String coordinates : new String[] {"1,2 NaN,4 5,6", "1,2 3,NaN 5,6", "1,2,10 3,4,20 NaN,6,30"}) {
			SourceGeometryLiteral source = source("<LineString><coordinates>" + coordinates
					+ "</coordinates></LineString>");

			JenaGeoSparqlException exception = assertThrows(coordinates, JenaGeoSparqlException.class,
					source::asGeometryWrapper);
			assertTrue(coordinates, exception.getMessage().contains("ordinates must be finite"));
		}
	}

	private static SourceGeometryLiteral source(String lexicalForm) {
		Literal literal = VALUE_FACTORY.createLiteral(lexicalForm, GeoConstants.GEO_KML_LITERAL);
		return JenaGeometryAdapter.toSourceGeometryLiteral(literal);
	}
}
