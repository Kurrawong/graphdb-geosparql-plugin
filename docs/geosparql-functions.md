# GeoSPARQL functions reference

This page lists the SPARQL extension functions registered by the GraphDB GeoSPARQL plugin. As in the
[GraphDB SPARQL functions reference](https://graphdb.ontotext.com/documentation/11.5/sparql-functions-reference.html),
each signature shows the result type before the function name.

The plugin supports the 37 non-topological GeoSPARQL 1.1 filter functions identified by
[Requirements 39 and 40](https://docs.ogc.org/is/22-047r1/22-047r1.html#_non_topological_query_functions) for its
Jena-aligned, non-DGGS geometry-literal profile. This bounded profile does not claim complete Clause 10.9 or complete
GeoSPARQL 1.1 conformance. The plugin also registers the topological, conversion, compatibility, and extension
functions listed below; those functions are outside the 37-function profile.

Use these prefixes with the signatures below:

```sparql
PREFIX geo: <http://www.opengis.net/ont/geosparql#>
PREFIX geof: <http://www.opengis.net/def/function/geosparql/>
PREFIX geoext: <http://rdf.useekm.com/ext#>
PREFIX uom: <http://www.opengis.net/def/uom/OGC/1.0/>
```

## Signature types

| Type | Meaning |
| --- | --- |
| `geomLiteral` | A source geometry literal with datatype `geo:wktLiteral`, `geo:gmlLiteral`, or `geo:geoJSONLiteral`. |
| `numeric` | A literal with a valid numeric XSD datatype. |
| `doubleLiteral` | A literal whose lexical form can be parsed as an `xsd:double`. This type is used only by compatibility and extension functions. |
| `uri` | An IRI or a simple `xsd:anyURI` literal. |
| `xsd:string` | A simple string literal without a language tag. |

Invalid RDF terms, malformed geometry literals, unsupported coordinate reference systems, incompatible units, and
ineligible geometry types produce a SPARQL expression error. Geometry results normally retain the datatype and
coordinate reference system of the first source geometry literal. See
[Geometry serialization and conversion](geosparql-geometry-serialization.md) for format-specific result rules and
[GeoSPARQL CRS deployment](geosparql-crs-deployment.md) for runtime CRS requirements.

## Requirement 39 query functions

These 23 functions form the Simple Features part of the bounded query-function profile.

| Function | Description |
| --- | --- |
| `geomLiteral geof:boundary(geomLiteral geometry)` | Returns the topological boundary of `geometry`. |
| `geomLiteral geof:boundingCircle(geomLiteral geometry)` | Returns a conservative polygonal representation of the planar minimum bounding circle. |
| `geomLiteral geof:metricBuffer(geomLiteral geometry, numeric radius)` | Returns a buffer whose radius is measured in metres. The source CRS must support a linear metre radius. |
| `geomLiteral geof:buffer(geomLiteral geometry, numeric radius, uri unit)` | Returns a buffer using `radius` in the specified unit. |
| `geomLiteral geof:centroid(geomLiteral geometry)` | Returns the centroid of `geometry`. |
| `geomLiteral geof:convexHull(geomLiteral geometry)` | Returns the convex hull of `geometry`. |
| `geomLiteral geof:concaveHull(geomLiteral geometry)` | Returns the planar concave hull of the complete input vertex set, without holes. |
| `xsd:integer geof:coordinateDimension(geomLiteral geometry)` | Returns the number of ordinates in the coordinate layout: 2, 3, or 4. |
| `geomLiteral geof:difference(geomLiteral left, geomLiteral right)` | Returns the point-set difference of `left` and `right` in the CRS of `left`. |
| `xsd:integer geof:dimension(geomLiteral geometry)` | Returns the topological dimension. A heterogeneous collection returns its largest member dimension. |
| `xsd:double geof:metricDistance(geomLiteral left, geomLiteral right)` | Returns the shortest distance in metres, calculated in the CRS of `left`. |
| `xsd:double geof:distance(geomLiteral left, geomLiteral right, uri unit)` | Returns the shortest distance in the specified unit, calculated in the CRS of `left`. |
| `geomLiteral geof:envelope(geomLiteral geometry)` | Returns the axis-aligned bounding rectangle of `geometry`. |
| `xsd:anyURI geof:geometryType(geomLiteral geometry)` | Returns the Simple Features class IRI for the geometry type. |
| `geomLiteral geof:intersection(geomLiteral left, geomLiteral right)` | Returns the point-set intersection of `left` and `right` in the CRS of `left`. |
| `xsd:boolean geof:is3D(geomLiteral geometry)` | Returns `true` when the coordinate layout contains a Z ordinate. |
| `xsd:boolean geof:isEmpty(geomLiteral geometry)` | Returns `true` when `geometry` contains no coordinates. |
| `xsd:boolean geof:isMeasured(geomLiteral geometry)` | Returns `true` when the coordinate layout contains an M ordinate. |
| `xsd:boolean geof:isSimple(geomLiteral geometry)` | Returns `true` when `geometry` is simple under the JTS Simple Features rules. |
| `xsd:integer geof:spatialDimension(geomLiteral geometry)` | Returns the number of spatial coordinate dimensions. |
| `geomLiteral geof:symDifference(geomLiteral left, geomLiteral right)` | Returns the points that occur in either input but not in both, in the CRS of `left`. |
| `geomLiteral geof:transform(geomLiteral geometry, uri targetSrs)` | Transforms the geometry coordinates to `targetSrs`. |
| `geomLiteral geof:union(geomLiteral left, geomLiteral right)` | Returns the point-set union of `left` and `right` in the CRS of `left`. |

## Requirement 40 query functions

These 14 functions form the non-Simple Features part of the bounded query-function profile.

| Function | Description |
| --- | --- |
| `xsd:double geof:metricArea(geomLiteral geometry)` | Returns area in square metres. Geographic source CRSes are not supported. |
| `xsd:double geof:area(geomLiteral geometry, uri unit)` | Returns area in the square of the specified linear unit. Geographic source CRSes are not supported. |
| `geomLiteral geof:geometryN(geomLiteral geometry, numeric index)` | Returns the direct geometry member at the one-based integral `index`. |
| `xsd:double geof:metricLength(geomLiteral geometry)` | Returns length in metres. |
| `xsd:double geof:length(geomLiteral geometry, uri unit)` | Returns length in the specified linear unit. |
| `xsd:double geof:maxX(geomLiteral geometry)` | Returns the largest X coordinate according to the source SRS axes. |
| `xsd:double geof:maxY(geomLiteral geometry)` | Returns the largest Y coordinate according to the source SRS axes. |
| `xsd:double geof:maxZ(geomLiteral geometry)` | Returns the largest finite Z ordinate. An XY or XYM geometry produces an error. |
| `xsd:double geof:minX(geomLiteral geometry)` | Returns the smallest X coordinate according to the source SRS axes. |
| `xsd:double geof:minY(geomLiteral geometry)` | Returns the smallest Y coordinate according to the source SRS axes. |
| `xsd:double geof:minZ(geomLiteral geometry)` | Returns the smallest finite Z ordinate. An XY or XYM geometry produces an error. |
| `xsd:integer geof:numGeometries(geomLiteral geometry)` | Returns the number of direct structural geometry members. An atomic geometry counts as one. |
| `xsd:double geof:perimeter(geomLiteral geometry, uri unit)` | Returns perimeter in the specified linear unit. Non-area members use their length. |
| `xsd:double geof:metricPerimeter(geomLiteral geometry)` | Returns perimeter in metres. Non-area members use their length. |

## Topological functions

All topological functions take two source geometry literals. The right geometry is transformed to the CRS of the left
geometry when required. The Simple Features, Egenhofer, and RCC8 relation names follow
[GeoSPARQL 1.1](https://docs.ogc.org/is/22-047r1/22-047r1.html).

### Simple Features relations

| Function | Description |
| --- | --- |
| `xsd:boolean geof:sfEquals(geomLiteral left, geomLiteral right)` | Tests Simple Features spatial equality. |
| `xsd:boolean geof:sfDisjoint(geomLiteral left, geomLiteral right)` | Tests whether the geometries have no point in common. |
| `xsd:boolean geof:sfIntersects(geomLiteral left, geomLiteral right)` | Tests whether the geometries have at least one point in common. |
| `xsd:boolean geof:sfTouches(geomLiteral left, geomLiteral right)` | Tests whether the geometries touch without overlapping interiors. |
| `xsd:boolean geof:sfCrosses(geomLiteral left, geomLiteral right)` | Tests the Simple Features crosses relation. |
| `xsd:boolean geof:sfWithin(geomLiteral left, geomLiteral right)` | Tests whether `left` is within `right`. |
| `xsd:boolean geof:sfContains(geomLiteral left, geomLiteral right)` | Tests whether `left` contains `right`. |
| `xsd:boolean geof:sfOverlaps(geomLiteral left, geomLiteral right)` | Tests the Simple Features overlaps relation. |

### Egenhofer relations

| Function | Description |
| --- | --- |
| `xsd:boolean geof:ehEquals(geomLiteral left, geomLiteral right)` | Tests Egenhofer equality. |
| `xsd:boolean geof:ehDisjoint(geomLiteral left, geomLiteral right)` | Tests the Egenhofer disjoint relation. |
| `xsd:boolean geof:ehMeet(geomLiteral left, geomLiteral right)` | Tests the Egenhofer meet relation. |
| `xsd:boolean geof:ehOverlap(geomLiteral left, geomLiteral right)` | Tests the Egenhofer overlap relation. |
| `xsd:boolean geof:ehCovers(geomLiteral left, geomLiteral right)` | Tests whether `left` covers `right`. |
| `xsd:boolean geof:ehCoveredBy(geomLiteral left, geomLiteral right)` | Tests whether `left` is covered by `right`. |
| `xsd:boolean geof:ehInside(geomLiteral left, geomLiteral right)` | Tests whether `left` is inside `right`. |
| `xsd:boolean geof:ehContains(geomLiteral left, geomLiteral right)` | Tests whether `left` contains `right`. |

### RCC8 relations

RCC8 functions apply to area/area geometry pairs.

| Function | Description |
| --- | --- |
| `xsd:boolean geof:rcc8eq(geomLiteral left, geomLiteral right)` | Tests equality. |
| `xsd:boolean geof:rcc8dc(geomLiteral left, geomLiteral right)` | Tests whether the geometries are disconnected. |
| `xsd:boolean geof:rcc8ec(geomLiteral left, geomLiteral right)` | Tests whether the geometries are externally connected. |
| `xsd:boolean geof:rcc8po(geomLiteral left, geomLiteral right)` | Tests whether the geometries partially overlap. |
| `xsd:boolean geof:rcc8tppi(geomLiteral left, geomLiteral right)` | Tests the tangential proper-part inverse relation. |
| `xsd:boolean geof:rcc8tpp(geomLiteral left, geomLiteral right)` | Tests the tangential proper-part relation. |
| `xsd:boolean geof:rcc8ntpp(geomLiteral left, geomLiteral right)` | Tests the non-tangential proper-part relation. |
| `xsd:boolean geof:rcc8ntppi(geomLiteral left, geomLiteral right)` | Tests the non-tangential proper-part inverse relation. |

### Intersection-pattern relation

| Function | Description |
| --- | --- |
| `xsd:boolean geof:relate(geomLiteral left, geomLiteral right, xsd:string pattern)` | Tests the geometries against a DE-9IM intersection pattern. |

## Geometry conversion and CRS functions

| Function | Description |
| --- | --- |
| `geo:geoJSONLiteral geof:asGeoJSON(geomLiteral geometry)` | Converts `geometry` to CRS84 GeoJSON. |
| `geo:wktLiteral geof:asWKT(geomLiteral geometry)` | Converts `geometry` to WKT without changing its CRS. |
| `geo:gmlLiteral geof:asGML(geomLiteral geometry, xsd:string profile)` | Converts `geometry` to the supported GML 3.2 geometry-fragment profile. |
| `xsd:anyURI geof:getSRID(geomLiteral geometry)` | Returns the source geometry literal's CRS URI. |

The `geof:asGML` profile string must be
`http://www.opengis.net/def/profile/ogc/2.0/gml-sf0`.

## Compatibility functions

The following overloads and aliases are retained for GraphDB compatibility. They are not members of the bounded
37-function profile.

| Function | Description |
| --- | --- |
| `xsd:double geof:distance(geomLiteral left, geomLiteral right)` | Returns planar XY distance in the coordinate units of `left`, after CRS alignment. |
| `geomLiteral geof:buffer(geomLiteral geometry, doubleLiteral radius)` | Returns a buffer whose radius uses the source CRS coordinate unit. |
| `xsd:integer geo:coordinateDimension(geomLiteral geometry)` | Compatibility alias for coordinate dimension. |
| `xsd:integer geo:dimension(geomLiteral geometry)` | Compatibility alias for topological dimension. |
| `xsd:integer geo:spatialDimension(geomLiteral geometry)` | Compatibility alias for spatial dimension. |
| `xsd:boolean geo:isEmpty(geomLiteral geometry)` | Compatibility alias for the empty-geometry test. |
| `xsd:boolean geo:isSimple(geomLiteral geometry)` | Compatibility alias for the Simple Features simplicity test. |

## GraphDB extension functions

These functions use the `http://rdf.useekm.com/ext#` namespace. They are GraphDB extension functions, not GeoSPARQL
functions.

| Function | Description |
| --- | --- |
| `xsd:double geoext:area(geomLiteral geometry)` | Returns planar JTS area in the square of the source coordinate unit. |
| `geomLiteral geoext:closestPoint(geomLiteral left, geomLiteral right)` | Returns the nearest point on `left` to `right`, in the CRS of `left`. |
| `xsd:boolean geoext:containsProperly(geomLiteral left, geomLiteral right)` | Tests whether `left` properly contains `right`. |
| `xsd:boolean geoext:coveredBy(geomLiteral left, geomLiteral right)` | Tests whether `left` is covered by `right`. |
| `xsd:boolean geoext:covers(geomLiteral left, geomLiteral right)` | Tests whether `left` covers `right`. |
| `xsd:double geoext:hausdorffDistance(geomLiteral left, geomLiteral right)` | Returns the JTS Hausdorff similarity measure after CRS alignment. Identical geometries return `1.0`. |
| `geomLiteral geoext:shortestLine(geomLiteral left, geomLiteral right)` | Returns the line between the nearest points of the two geometries, in the CRS of `left`. |
| `geomLiteral geoext:simplify(geomLiteral geometry, doubleLiteral tolerance)` | Simplifies the geometry with the Douglas-Peucker algorithm. |
| `geomLiteral geoext:simplifyPreserveTopology(geomLiteral geometry, doubleLiteral tolerance)` | Simplifies the geometry while preserving topology. |
| `xsd:boolean geoext:isValid(geomLiteral geometry)` | Tests whether the geometry literal parses and the geometry is valid. |
