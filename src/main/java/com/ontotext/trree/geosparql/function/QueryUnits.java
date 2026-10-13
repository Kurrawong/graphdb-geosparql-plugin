package com.ontotext.trree.geosparql.function;

import com.ontotext.trree.geosparql.jena.GeoSparqlUnits;
import org.apache.jena.geosparql.implementation.UnitsConversionException;
import org.apache.jena.geosparql.implementation.vocabulary.Unit_URI;

import java.util.Map;

final class QueryUnits {
	private static final double LIGHT_YEAR_IN_METRES = 9.460528405e15;
	private static final Map<String, String> LENGTH_ALIASES = Map.of(
			GeoSparqlUnits.URI_CENTIMETRE.stringValue(), Unit_URI.CENTIMETRE_URL,
			GeoSparqlUnits.URI_KILOMETRE.stringValue(), Unit_URI.KILOMETRE_URL,
			GeoSparqlUnits.URI_MILLIMETRE.stringValue(), Unit_URI.MILLIMETRE_URL,
			GeoSparqlUnits.URI_FOOT.stringValue(), Unit_URI.FOOT_URL,
			GeoSparqlUnits.URI_US_SURVEY_FOOT.stringValue(), Unit_URI.US_SURVEY_FOOT_URL,
			GeoSparqlUnits.URI_INCH.stringValue(), Unit_URI.INCH_URL,
			GeoSparqlUnits.URI_MILE.stringValue(), Unit_URI.MILE_URL,
			GeoSparqlUnits.URI_NAUTICAL_MILE.stringValue(), Unit_URI.NAUTICAL_MILE_URL,
			GeoSparqlUnits.URI_YARD.stringValue(), Unit_URI.YARD_URL);

	private QueryUnits() {
	}

	static ResolvedUnit resolve(String uri) {
		if (GeoSparqlUnits.URI_LIGHT_YEAR.stringValue().equals(uri)) {
			return new ResolvedUnit(Unit_URI.METRE_URL, LIGHT_YEAR_IN_METRES);
		}
		return new ResolvedUnit(LENGTH_ALIASES.getOrDefault(uri, uri), 1.0);
	}

	record ResolvedUnit(String unitUri, double scale) {
		double fromNative(double value, int unitPower) {
			return value / Math.pow(scale, unitPower);
		}

		double toNative(double value) {
			double converted = value * scale;
			if (!Double.isFinite(converted)) {
				throw new UnitsConversionException("Measurement is not representable in the calculation unit.");
			}
			return converted;
		}
	}
}
