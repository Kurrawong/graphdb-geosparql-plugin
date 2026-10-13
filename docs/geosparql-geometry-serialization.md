# Geometry serialization and conversion

The plugin supports WKT, GML, and GeoJSON geometry serializations as a bounded interoperability feature. The
supported RDF datatypes, geometry serialization properties, and conversion functions are:

| Format | RDF datatype | Geometry serialization property | Conversion function |
| --- | --- | --- | --- |
| WKT | `geo:wktLiteral` | `geo:asWKT` | `geof:asWKT(geometry)` |
| GML | `geo:gmlLiteral` | `geo:asGML` | `geof:asGML(geometry, profile)` |
| GeoJSON | `geo:geoJSONLiteral` | `geo:asGeoJSON` | `geof:asGeoJSON(geometry)` |

Conversion results are geometry literals that can be passed to the supported conversion functions and GeoSPARQL
topological relations. The guarantee is semantic: it does not promise preservation of the source lexical form,
decimal spelling, metadata, or every coordinate ordinate.

## Conversion contract

`geof:asWKT` preserves the source coordinate reference system and supported coordinate layout without transforming
the geometry. A native XYZ GeoJSON literal therefore becomes XYZ WKT with CRS84 as its coordinate reference system.

`geof:asGML` preserves the source coordinate reference system and axis semantics without transforming the geometry.
XY output is supported. XYZ output requires a genuinely three-dimensional source CRS. XYZ under a two-dimensional
CRS, including native XYZ GeoJSON under CRS84, and measured XYM or XYZM layouts produce a SPARQL expression error.

`geof:asGeoJSON` produces CRS84 longitude/latitude coordinates. Non-CRS84 WKT and GML inputs are transformed to
CRS84. GeoJSON generated from WKT, GML, or a geometry-changing function is XY: Z and M ordinates are omitted because
the conversion cannot establish RFC 7946 altitude semantics for them. A native GeoJSON identity conversion may
preserve a valid XYZ altitude.

These rules give the following round-trip boundaries:

- Direct WKT-to-GML-to-WKT and GML-to-WKT-to-GML conversions retain the source SRS and axis semantics within the
  supported GML dimension contract.
- A round trip that passes through generated GeoJSON compares the CRS84-transformed XY geometry. It does not retain a
  non-CRS84 identifier, the source lexical form, or vertical and measured ordinates.
- Native XYZ GeoJSON converts to XYZ WKT. It cannot convert directly to GML because CRS84 is two-dimensional. A later
  conversion from that WKT to GeoJSON is a generated-GeoJSON path and emits XY.
- GeoJSON output follows the JTS 1.20 ordinate formatter used by Apache Jena 6.2. It is deterministic for those
  versions but is not a fixed-decimal or lossless-decimal format.

## GeoJSON input and output

`geo:geoJSONLiteral` is fixed to CRS84. It accepts the seven RFC 7946 Geometry roots: Point, MultiPoint, LineString,
MultiLineString, Polygon, MultiPolygon, and GeometryCollection. GeometryCollection members are checked recursively.
Feature and FeatureCollection roots, a legacy `crs` member, malformed geometry structures, non-finite coordinates,
mixed coordinate dimensions, and positions with more than three ordinates are rejected.

Coordinates must be homogeneous XY or XYZ and non-empty coordinate values must be inside the CRS84 domain. The third
ordinate of native XYZ GeoJSON is altitude. A structurally and dimensionally valid finite `bbox` and genuine foreign
members are accepted but ignored. They are not preserved or re-emitted. Members that define incompatible GeoJSON
object types, such as `geometry`, `properties`, or `features`, are rejected when they occur on an actual Geometry
object.

Generated GeoJSON omits the legacy `crs` member and writes polygon exteriors counter-clockwise. Antimeridian-crossing,
polar, and large or global inputs retain Apache Jena's vertex-by-vertex CRS transformation behavior. The plugin does
not add densification, antimeridian splitting, or ellipsoidal edge modelling, so this behavior is not a universal
antimeridian or global-geometry guarantee.

## GML profile

The `geof:asGML` profile argument must be a simple literal or explicitly `xsd:string` literal whose lexical form is
exactly:

```text
http://www.opengis.net/def/profile/ogc/2.0/gml-sf0
```

Language-tagged literals, differently typed literals, IRIs, and other profile strings produce a SPARQL expression
error. The selector identifies the supported Apache Jena GML 3.2 geometry-fragment subset: Point, LineString,
Polygon, MultiPoint, MultiCurve, MultiSurface, and MultiGeometry output. Non-empty XY fragments are validated against
the corresponding GML 3.2.1 geometry content models. This is not complete GML Simple Features application-schema
conformance; applications embedding a fragment remain responsible for document-level requirements such as `gml:id`.

## Empty geometries

A zero-length `geo:geoJSONLiteral` represents an empty, zero-dimensional CRS84 Point with XY coordinate and spatial
dimensions. `geof:asWKT` converts it to `POINT EMPTY`, `geof:asGeoJSON` emits a typed empty Point object, and
`geof:asGML` emits the zero-length `geo:gmlLiteral` form. Typed empty WKT and GeoJSON roots remain reusable; converting
an empty value through the zero-length GML form loses the original empty geometry type because that form represents
an empty Point.

Empty geometry serializations retain empty topology. They are stored as non-spatial sentinel documents for exact
evaluation and are not treated as Lucene spatial candidates.

## Geometry serialization indexing

Each WKT, GML, or GeoJSON geometry serialization attached to a Geometry is a distinct source geometry literal. No
format is preferred. A GeoSPARQL property relation has existential source-literal semantics: it succeeds when any
source-literal pair satisfies the relation.

Full indexing and incremental additions, replacements, and removals discover all three serialization properties for
direct Geometry resources and for Features through their default Geometries. Lucene index geometry is used only for
coarse candidate lookup. Exact evaluation continues to use the CRS-preserving source geometry literal.

## Upgrading an existing repository

An index built without GeoJSON serialization discovery is incompatible because it may omit existing
`geo:asGeoJSON` statements. The plugin fails closed instead of returning incomplete spatial results. After upgrading,
force reindex each enabled repository so the index is rebuilt under the WKT, GML, and GeoJSON discovery policy:

```sparql
PREFIX plugin: <http://www.ontotext.com/plugins/geosparql#>

INSERT DATA {
  [] plugin:forceReindex true
}
```

Resolve any reported geometry, CRS-data, storage, or configuration failure and run the update again. A failed or
rolled-back rebuild does not mark the index compatible.

## Scope limits

KML and DGGS geometry serializations are unsupported. The plugin does not provide `geof:asKML` or `geof:asDGGS`.
The contract above covers reusable WKT, GML, and GeoJSON geometry serialization, conversion, indexing, and exact
topological evaluation. It does not claim complete GeoSPARQL 1.1 conformance.
