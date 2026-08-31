# GeoSPARQL functions and predicates reference

The GraphDB GeoSPARQL plugin provides GeoSPARQL functions for working with geometry literals, including geometry
operations, measurements, coordinate transformations, spatial relationships, and format conversion. It also provides
GeoSPARQL predicates for indexed spatial queries. The tables below list the supported functions, signatures, and
predicates. Each function signature shows the result type before the function name.

For repository setup, index configuration, and a first query, see
[Using the GeoSPARQL plugin](geosparql-usage.md).

Use these prefixes with the examples and signatures below:

```sparql
PREFIX geo: <http://www.opengis.net/ont/geosparql#>
PREFIX geof: <http://www.opengis.net/def/function/geosparql/>
PREFIX geoext: <http://rdf.useekm.com/ext#>
PREFIX uom: <http://www.opengis.net/def/uom/OGC/1.0/>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
```

## Signature types

| Type | Meaning |
| --- | --- |
| `geomLiteral` | A source geometry literal with datatype `geo:wktLiteral`, `geo:gmlLiteral`, or `geo:geoJSONLiteral`. |
| `numeric` | A literal with a valid numeric XSD datatype. |
| `doubleLiteral` | A literal whose lexical form can be parsed as an `xsd:double`. This type is used by alternative signatures and GraphDB extension functions. |
| `uri` | An IRI or a simple `xsd:anyURI` literal. |
| `xsd:string` | A simple string literal without a language tag. |

Invalid RDF terms, malformed geometry literals, unsupported coordinate reference systems, and incompatible units
generally produce a SPARQL expression error. Geometry-type eligibility and empty-geometry behavior are
function-specific, as described below. Geometry results normally retain the datatype and coordinate reference system
of the first source geometry literal. See
[Geometry serialization and conversion](geosparql-geometry-serialization.md) for format-specific result rules and
[GeoSPARQL CRS deployment](geosparql-crs-deployment.md) for runtime CRS requirements.

## Geometry operations

| Function | Description |
| --- | --- |
| `geomLiteral geof:boundary(geomLiteral geometry)` | Returns the topological boundary of `geometry`. |
| `geomLiteral geof:boundingCircle(geomLiteral geometry)` | Returns a conservative polygonal representation of the planar minimum bounding circle. |
| `geomLiteral geof:metricBuffer(geomLiteral geometry, numeric radius)` | Returns a buffer whose radius is measured in metres. The input geometry must use a CRS with linear units; geographic CRSs are not supported. |
| `geomLiteral geof:buffer(geomLiteral geometry, numeric radius, uri unit)` | Returns a buffer using `radius` in the specified unit. |
| `geomLiteral geof:centroid(geomLiteral geometry)` | Returns the centroid of `geometry`. |
| `geomLiteral geof:convexHull(geomLiteral geometry)` | Returns the convex hull of `geometry`. |
| `geomLiteral geof:concaveHull(geomLiteral geometry)` | Returns the planar concave hull of the complete input vertex set, without holes. |
| `geomLiteral geof:difference(geomLiteral left, geomLiteral right)` | Returns the point-set difference of `left` and `right` in the CRS of `left`. |
| `geomLiteral geof:envelope(geomLiteral geometry)` | Returns the axis-aligned bounding rectangle of `geometry`. |
| `geomLiteral geof:intersection(geomLiteral left, geomLiteral right)` | Returns the point-set intersection of `left` and `right` in the CRS of `left`. |
| `geomLiteral geof:symDifference(geomLiteral left, geomLiteral right)` | Returns the points that occur in either input but not in both, in the CRS of `left`. |
| `geomLiteral geof:union(geomLiteral left, geomLiteral right)` | Returns the point-set union of `left` and `right` in the CRS of `left`. |

## Measurements

| Function | Description |
| --- | --- |
| `xsd:double geof:metricDistance(geomLiteral left, geomLiteral right)` | Returns the shortest distance in metres, calculated in the CRS of `left`. |
| `xsd:double geof:distance(geomLiteral left, geomLiteral right, uri unit)` | Returns the shortest distance in the specified unit, calculated in the CRS of `left`. |
| `xsd:double geof:metricArea(geomLiteral geometry)` | Returns area in square metres. Geographic CRSs are not supported; transform geographic data to a suitable projected CRS first. |
| `xsd:double geof:area(geomLiteral geometry, uri unit)` | Returns area in the square of the specified linear unit. Geographic CRSs are not supported; transform geographic data to a suitable projected CRS first. |
| `xsd:double geof:metricLength(geomLiteral geometry)` | Returns length in metres. |
| `xsd:double geof:length(geomLiteral geometry, uri unit)` | Returns length in the specified linear unit. |
| `xsd:double geof:perimeter(geomLiteral geometry, uri unit)` | Returns perimeter in the specified linear unit. Non-polygon inputs and collection members contribute their length. |
| `xsd:double geof:metricPerimeter(geomLiteral geometry)` | Returns perimeter in metres. Non-polygon inputs and collection members contribute their length. |

## Geometry information

| Function | Description |
| --- | --- |
| `xsd:integer geof:coordinateDimension(geomLiteral geometry)` | Returns the number of ordinates in the coordinate layout: 2, 3, or 4. |
| `xsd:integer geof:dimension(geomLiteral geometry)` | Returns the topological dimension. A heterogeneous collection returns its largest member dimension. |
| `geomLiteral geof:geometryN(geomLiteral geometry, numeric index)` | Returns the direct geometry member at the one-based integral `index`. |
| `xsd:anyURI geof:geometryType(geomLiteral geometry)` | Returns the Simple Features class IRI for the geometry type. |
| `xsd:boolean geof:is3D(geomLiteral geometry)` | Returns `true` when the coordinate layout contains a Z ordinate. |
| `xsd:boolean geof:isEmpty(geomLiteral geometry)` | Returns `true` when `geometry` contains no coordinates. |
| `xsd:boolean geof:isMeasured(geomLiteral geometry)` | Returns `true` when the coordinate layout contains an M ordinate. |
| `xsd:boolean geof:isSimple(geomLiteral geometry)` | Returns `true` when `geometry` is simple under the Simple Features rules. |
| `xsd:double geof:maxX(geomLiteral geometry)` | Returns the largest X coordinate according to the source SRS axes. |
| `xsd:double geof:maxY(geomLiteral geometry)` | Returns the largest Y coordinate according to the source SRS axes. |
| `xsd:double geof:maxZ(geomLiteral geometry)` | Returns the largest finite Z ordinate. An XY or XYM geometry produces an error. |
| `xsd:double geof:minX(geomLiteral geometry)` | Returns the smallest X coordinate according to the source SRS axes. |
| `xsd:double geof:minY(geomLiteral geometry)` | Returns the smallest Y coordinate according to the source SRS axes. |
| `xsd:double geof:minZ(geomLiteral geometry)` | Returns the smallest finite Z ordinate. An XY or XYM geometry produces an error. |
| `xsd:integer geof:numGeometries(geomLiteral geometry)` | Returns the number of direct structural geometry members. An atomic geometry counts as one. |
| `xsd:integer geof:spatialDimension(geomLiteral geometry)` | Returns the number of spatial coordinate dimensions. |

## Coordinate reference systems

| Function | Description |
| --- | --- |
| `geomLiteral geof:transform(geomLiteral geometry, uri targetSrs)` | Transforms the geometry coordinates to `targetSrs`. |
| `xsd:anyURI geof:getSRID(geomLiteral geometry)` | Returns the source geometry literal's CRS URI. |

## Spatial relationships

The Simple Features, Egenhofer, and RCC8 functions take two source geometry literals. The right geometry is
transformed to the CRS of the left geometry when required. The relation names follow
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

RCC8 functions are defined for two area geometries, such as Polygon or MultiPolygon. If either input is not an area
geometry, the function returns `false`.

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

## GeoSPARQL predicates

GeoSPARQL topological relations can be queried in function form or predicate form:

```sparql
# Function form
FILTER(geof:sfWithin(?leftGeometry, ?rightGeometry))

# Predicate form
?left geo:sfWithin ?right .
```

Use a `geof:` function when the query already has two geometry literals to compare. Functions evaluate the relation
directly, do not use the GeoSPARQL index, and remain available when the plugin is not enabled. Use the corresponding
`geo:` predicate to find Features or Geometries that have a spatial relationship. Predicate queries use the GeoSPARQL
index and require the plugin to be enabled.

At least one side of a predicate query must already have a value. When one side is unknown, the index finds possible
matches and the plugin verifies the spatial relationship. When both sides have values, the plugin evaluates the pair
directly without searching the index.

The subject and object may be `geo:Feature` or `geo:Geometry` resources. For a Feature, the plugin uses the Geometry
resources linked through `geo:hasDefaultGeometry`. For a Geometry, it uses the geometry literals supplied through
`geo:asWKT`, `geo:asGML`, or `geo:asGeoJSON`. A `geo:wktLiteral`, `geo:gmlLiteral`, or `geo:geoJSONLiteral` may also
be supplied as a bound value on either side. This bound-literal form is a GraphDB query convenience. GeoSPARQL defines
relation properties between `geo:SpatialObject` resources, so queries intended to be portable should use Feature or
Geometry resources as predicate operands and use `geof:` functions to compare geometry literals.

Because a triple pattern with a literal subject cannot match an RDF graph, bind a subject-side geometry literal to a
variable first:

```sparql
VALUES ?leftGeometry { "POINT(153 -27)"^^geo:wktLiteral }
?leftGeometry geo:sfWithin ?right .
```

When one side is unbound, matching Feature and Geometry resources are returned from the GeoSPARQL index.

### Indexed spatial relationship predicates

The predicate semantics correspond to the matching topological functions described under
[Spatial relationships](#spatial-relationships) and to the relation definitions in the
[GeoSPARQL specification](https://docs.ogc.org/is/22-047r1/22-047r1.html).

#### Simple Features predicates

| Predicate | Description |
| --- | --- |
| `geo:sfEquals` | Relates spatial objects that are spatially equal under Simple Features semantics. |
| `geo:sfDisjoint` | Relates spatial objects that have no point in common. |
| `geo:sfIntersects` | Relates spatial objects that have at least one point in common. |
| `geo:sfTouches` | Relates spatial objects that touch without overlapping interiors. |
| `geo:sfWithin` | Relates a subject spatial object that is within the object spatial object. |
| `geo:sfContains` | Relates a subject spatial object that contains the object spatial object. |
| `geo:sfOverlaps` | Relates spatial objects that overlap under Simple Features semantics. |
| `geo:sfCrosses` | Relates spatial objects that cross under Simple Features semantics. |

#### Egenhofer predicates

| Predicate | Description |
| --- | --- |
| `geo:ehEquals` | Relates spatial objects that are spatially equal under Egenhofer semantics. |
| `geo:ehDisjoint` | Relates spatial objects that are disjoint under Egenhofer semantics. |
| `geo:ehMeet` | Relates spatial objects that meet under Egenhofer semantics. |
| `geo:ehOverlap` | Relates spatial objects that overlap under Egenhofer semantics. |
| `geo:ehCovers` | Relates a subject spatial object that covers the object spatial object. |
| `geo:ehCoveredBy` | Relates a subject spatial object that is covered by the object spatial object. |
| `geo:ehInside` | Relates a subject spatial object that is inside the object spatial object. |
| `geo:ehContains` | Relates a subject spatial object that contains the object spatial object. |

#### RCC8 predicates

RCC8 predicates are defined between two area geometries, such as Polygon or MultiPolygon. The relation evaluates to
`false` for a pair containing a non-area geometry, so the predicate does not match that pair.

| Predicate | Description |
| --- | --- |
| `geo:rcc8eq` | Relates equal regions. |
| `geo:rcc8dc` | Relates disconnected regions. |
| `geo:rcc8ec` | Relates externally connected regions. |
| `geo:rcc8po` | Relates partially overlapping regions. |
| `geo:rcc8tppi` | Relates a subject region that is the tangential proper-part inverse of the object region. |
| `geo:rcc8tpp` | Relates a subject region that is a tangential proper part of the object region. |
| `geo:rcc8ntpp` | Relates a subject region that is a non-tangential proper part of the object region. |
| `geo:rcc8ntppi` | Relates a subject region that is the non-tangential proper-part inverse of the object region. |

### GeoSPARQL data-model properties

The following ordinary RDF properties describe the geometry data used by this plugin. Querying these properties is an
ordinary repository triple lookup; it does not itself perform a spatial-index search. When the plugin is enabled, it
reads these statements when building or updating the spatial index.

| Property | Role |
| --- | --- |
| `geo:hasDefaultGeometry` | Links a Feature to its default Geometry resource. |
| `geo:asWKT` | Associates a Geometry resource with a WKT geometry literal. |
| `geo:asGML` | Associates a Geometry resource with a GML geometry literal. |
| `geo:asGeoJSON` | Associates a Geometry resource with a GeoJSON geometry literal. |

For example, the following update gives a Feature a default Geometry and supplies its WKT serialization:

```sparql
PREFIX geo: <http://www.opengis.net/ont/geosparql#>
PREFIX ex: <http://example.com/>

INSERT DATA {
  ex:feature geo:hasDefaultGeometry ex:geometry .
  ex:geometry geo:asWKT "POINT(153 -27)"^^geo:wktLiteral .
}
```

Use `geo:asGML` with a `geo:gmlLiteral` or `geo:asGeoJSON` with a `geo:geoJSONLiteral` to supply the corresponding
alternative serialization.

## Geometry conversion

| Function | Description |
| --- | --- |
| `geo:geoJSONLiteral geof:asGeoJSON(geomLiteral geometry)` | Converts `geometry` to CRS84 GeoJSON. |
| `geo:wktLiteral geof:asWKT(geomLiteral geometry)` | Converts `geometry` to WKT without changing its CRS. |
| `geo:gmlLiteral geof:asGML(geomLiteral geometry, xsd:string profile)` | Converts `geometry` to the supported GML 3.2 geometry-fragment profile. |

The `geof:asGML` profile string must be
`http://www.opengis.net/def/profile/ogc/2.0/gml-sf0`.

## Alternative signatures and aliases

The plugin also supports the following alternative function signatures and aliases. When called with parentheses,
the `geo:` entries below are plugin-registered SPARQL function aliases. GeoSPARQL also defines the same IRIs as RDF
properties when they appear in triple patterns.

For example, `geo:dimension(?geometryLiteral)` calculates a geometry literal's topological dimension, whereas
`?geometryResource geo:dimension ?storedDimension .` retrieves a value from an RDF triple.

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

In addition to the GeoSPARQL functions, GraphDB provides several useful extensions based on the
[USeekM](https://www.w3.org/2001/sw/wiki/USeekM) library. These functions use the `geoext:` prefix for the
`http://rdf.useekm.com/ext#` namespace; they are GraphDB extensions rather than GeoSPARQL functions.

| Function | Description |
| --- | --- |
| `xsd:double geoext:area(geomLiteral geometry)` | Calculates planar surface area in the square of the source CRS coordinate unit. |
| `geomLiteral geoext:closestPoint(geomLiteral left, geomLiteral right)` | Computes the point on `left` that is closest to `right`, in the CRS of `left`. |
| `xsd:boolean geoext:containsProperly(geomLiteral left, geomLiteral right)` | Tests whether `left` contains `right` and their boundaries do not intersect. |
| `xsd:boolean geoext:coveredBy(geomLiteral left, geomLiteral right)` | Tests whether every point of `left` is also a point of `right`. |
| `xsd:boolean geoext:covers(geomLiteral left, geomLiteral right)` | Tests whether every point of `right` is also a point of `left`. |
| `xsd:double geoext:hausdorffDistance(geomLiteral left, geomLiteral right)` | Measures geometric similarity after CRS alignment, normalized to the range `0`–`1`. Higher values indicate greater similarity; identical geometries return `1.0`. |
| `geomLiteral geoext:shortestLine(geomLiteral left, geomLiteral right)` | Computes the shortest line between the geometries and returns it as a LineString in the CRS of `left`. |
| `geomLiteral geoext:simplify(geomLiteral geometry, doubleLiteral tolerance)` | Simplifies the geometry with the [Douglas-Peucker algorithm](https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm), using `tolerance` as the maximum allowed deviation. |
| `geomLiteral geoext:simplifyPreserveTopology(geomLiteral geometry, doubleLiteral tolerance)` | Simplifies the geometry with the [Douglas-Peucker algorithm](https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm) while avoiding invalid derived geometries. |
| `xsd:boolean geoext:isValid(geomLiteral geometry)` | Tests whether the input is a valid geometry. |
