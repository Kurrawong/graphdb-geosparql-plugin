# Geometry serialization and conversion

The GraphDB GeoSPARQL plugin supports WKT, GML, and GeoJSON geometry literals. These formats can be stored using the
corresponding GeoSPARQL properties, used with GeoSPARQL functions and predicates, and converted using the functions
below.

Use these prefixes with the examples and function names on this page:

```sparql
PREFIX geo: <http://www.opengis.net/ont/geosparql#>
PREFIX geof: <http://www.opengis.net/def/function/geosparql/>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
```

| Format | RDF datatype | Geometry serialization property | Conversion function |
| --- | --- | --- | --- |
| WKT | `geo:wktLiteral` | `geo:asWKT` | `geof:asWKT(geometry)` |
| GML | `geo:gmlLiteral` | `geo:asGML` | `geof:asGML(geometry, profile)` |
| GeoJSON | `geo:geoJSONLiteral` | `geo:asGeoJSON` | `geof:asGeoJSON(geometry)` |

Conversion produces a new geometry literal. The rules below describe which coordinate reference system and
coordinate layout the result retains. Conversion may change the source text, numeric formatting, format-specific
metadata, or omit Z and M ordinates where described below.

## Coordinate layouts

The conversion rules use the following coordinate-layout notation:

| Layout | Ordinates |
| --- | --- |
| XY | Two horizontal spatial coordinates. |
| XYZ | Two horizontal spatial coordinates and a Z ordinate. In native GeoJSON, Z is altitude. |
| XYM | Two horizontal spatial coordinates and a non-spatial measure ordinate. |
| XYZM | Two horizontal spatial coordinates, a Z ordinate, and a non-spatial measure ordinate. |

## Conversion behavior

`geof:asWKT` preserves the source coordinate reference system and supported coordinate layout without transforming
the geometry. A native XYZ GeoJSON literal therefore becomes XYZ WKT with CRS84 as its coordinate reference system.

`geof:asGML` preserves the source coordinate reference system and axis semantics without transforming the geometry.
XY output is supported. XYZ output requires a genuinely three-dimensional source CRS. XYZ under a two-dimensional
CRS, including native XYZ GeoJSON under CRS84, and measured XYM or XYZM layouts produce a SPARQL expression error.

`geof:asGeoJSON` produces CRS84 longitude/latitude coordinates. Non-CRS84 WKT and GML inputs are transformed to
CRS84. GeoJSON generated from WKT, GML, or a geometry-changing function is XY: Z and M ordinates are omitted because
the conversion cannot establish RFC 7946 altitude semantics for them. A native GeoJSON identity conversion may
preserve a valid XYZ altitude.

These rules affect round trips as follows:

- Direct WKT-to-GML-to-WKT and GML-to-WKT-to-GML conversions retain the source CRS and axis semantics for the
  supported GML coordinate layouts.
- A round trip that passes through generated GeoJSON compares the CRS84-transformed XY geometry. It does not retain a
  non-CRS84 identifier, the source lexical form, or vertical and measured ordinates.
- Native XYZ GeoJSON converts to XYZ WKT but cannot convert directly to GML because CRS84 is two-dimensional.
  Converting that WKT back to GeoJSON produces generated GeoJSON and emits XY.
- GeoJSON numeric output is deterministic, but it is not a fixed-decimal or lossless-decimal representation.

## GeoJSON input and output

`geo:geoJSONLiteral` is fixed to CRS84. It accepts the seven [RFC 7946](https://www.rfc-editor.org/rfc/rfc7946)
Geometry roots: Point, MultiPoint, LineString, MultiLineString, Polygon, MultiPolygon, and GeometryCollection.
GeometryCollection members are checked recursively. Feature and FeatureCollection roots, a legacy `crs` member,
malformed geometry structures, non-finite coordinates, mixed coordinate dimensions, and positions with more than
three ordinates are rejected.

Coordinates must be homogeneous XY or XYZ and non-empty coordinate values must be inside the CRS84 domain. The third
ordinate of native XYZ GeoJSON is altitude. A structurally and dimensionally valid finite `bbox` and additional
members permitted by RFC 7946 are accepted but ignored. They are not preserved or re-emitted. Members reserved for
other GeoJSON object types, such as `geometry`, `properties`, or `features`, are rejected when they occur on a
Geometry object.

Generated GeoJSON omits the legacy `crs` member and writes polygon exteriors counter-clockwise. It transforms existing
vertices to CRS84 without densifying edges, splitting geometries at the antimeridian, or modelling edges on an
ellipsoid. Review the generated coordinates when converting antimeridian-crossing, polar, or large global geometries.

## GML profile

The `geof:asGML` profile argument must be a simple literal or explicitly `xsd:string` literal whose lexical form is
exactly:

```text
http://www.opengis.net/def/profile/ogc/2.0/gml-sf0
```

Language-tagged literals, differently typed literals, IRIs, and other profile strings produce a SPARQL expression
error. The profile selects the supported GML 3.2 geometry-fragment types: Point, LineString, Polygon, MultiPoint,
MultiCurve, MultiSurface, and MultiGeometry. Non-empty XY fragments are validated against the corresponding GML 3.2.1
geometry content models. `geof:asGML` returns a geometry fragment, not a complete GML application-schema document.
Applications embedding the fragment must supply document-level information such as `gml:id`.

## Empty geometries

A zero-length `geo:geoJSONLiteral` or `geo:gmlLiteral` is an empty Geometry. When evaluating or converting either
generic empty value, the plugin treats it as an empty CRS84 Point with XY coordinate and spatial dimensions.
Consequently, `geof:asWKT` produces `POINT EMPTY`, `geof:asGeoJSON` produces a typed empty Point object, and
`geof:asGML` produces a zero-length `geo:gmlLiteral`.

Typed empty WKT and GeoJSON values retain their geometry type across WKT and GeoJSON conversions. Passing an empty
value through the zero-length GML form loses that type because the plugin subsequently evaluates the generic empty
GML value as an empty Point.

Empty geometry serializations retain empty topology and remain available for relation evaluation. Because they have
no spatial extent, they are not returned by spatial-envelope candidate searches.

## Geometry serialization indexing

Each WKT, GML, or GeoJSON serialization attached to a Geometry is indexed independently, and no format is preferred.
A GeoSPARQL predicate matches when any pair of the available geometry serializations satisfies the relation.

During initial indexing and subsequent repository updates, the plugin reads all three serialization properties from
Geometry resources and from Features through their default Geometries. The spatial index finds possible matches, and
the plugin verifies each relation using the original geometry literal and its coordinate reference system.

## Upgrading an existing repository

After upgrading from a plugin version that did not index `geo:asGeoJSON` statements, force a reindex of each enabled
repository so existing WKT, GML, and GeoJSON geometry data is included:

```sparql
PREFIX plugin: <http://www.ontotext.com/plugins/geosparql#>

INSERT DATA {
  [] plugin:forceReindex true
}
```

Resolve any reported geometry, CRS-data, storage, or configuration failure and run the update again. If the rebuild
fails, indexed spatial predicate queries remain unavailable.
