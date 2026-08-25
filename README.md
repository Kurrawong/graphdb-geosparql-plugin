# GraphDB GeoSPARQL Plugin

This is the GraphDB GeoSPARQL plugin. More information about it is available in the GraphDB documentation here:
http://graphdb.ontotext.com/documentation/enterprise/geosparql-support.html

## Building the plugin

The plugin is a Maven project targeting GraphDB 11. JDK 21 is required to build the plugin and run it in GraphDB.

Run `mvn clean package` to build the plugin and execute the tests.

The package lifecycle also inspects the assembled plugin ZIP without Docker. It verifies that the plugin and supported
Apache SIS Derby runtime are present and that test-only or legacy CRS provider dependencies are absent.

The tests use the test-scoped Apache SIS `sis-embedded-data` dependency, whose EPSG data is subject to the
[EPSG Terms of Use](https://epsg.org/terms-of-use.html). This dependency is not included in the assembled plugin.

The built plugin can be found in the `target` directory:

- `geosparql-plugin-graphdb-plugin.zip`

### Packaging smoke test

The opt-in packaging smoke test requires Docker. It builds a temporary GraphDB 10.8.12 image with Java 21, installs
the assembled plugin ZIP, and uses externally supplied Apache SIS data to run an EPSG:3006 GeoSPARQL property relation
and five-metre distance filter function alongside a CRS84 property relation:

```bash
mvn -Pgraphdb-packaging-smoke verify
```

This test checks that the assembled plugin loads and executes in GraphDB with externally supplied Apache SIS CRS data.
It does not establish GraphDB 10.8 support or replace validation against the target GraphDB 11 runtime.

## Installing the plugin

External plugins are installed under `lib/plugins` in the GraphDB distribution
directory. To install the plugin follow these steps:

1. Remove `lib/plugins/geosparql-plugin`. Also remove `lib/plugins/graphdb-geosparql-plugin` if it exists from an
   older installation.
1. Unzip the built zip file in `lib/plugins`. This creates `lib/plugins/geosparql-plugin`.
1. Restart GraphDB.

## Rebuilding the GeoSPARQL index

Rebuilding regenerates the GeoSPARQL Lucene index from the current repository data and applies the currently configured
index settings. Run a rebuild when GraphDB reports an incompatible GeoSPARQL index, after changing index settings such
as the prefix tree or precision, or whenever the index needs to be recreated from repository data.

If the plugin is disabled, enabling it performs a full index build. If the plugin is already enabled, run this SPARQL
update to force a rebuild:

```sparql
PREFIX plugin: <http://www.ontotext.com/plugins/geosparql#>

INSERT DATA {
  [] plugin:forceReindex true
}
```

The update runs synchronously and may take significant time for a large repository. GraphDB logs
`Initializing force reindexing process` when the rebuild starts and `Indexing completed` when the indexing work
finishes. If the rebuild fails, resolve the reported geometry, CRS-data, storage, or configuration problem and run the
update again.

## Geometry serialization and conversion

The plugin supports reusable WKT, GML, and GeoJSON geometry literals and the `geof:asWKT`, `geof:asGML`, and
`geof:asGeoJSON` conversion functions. See
[Geometry serialization and conversion](docs/geosparql-geometry-serialization.md) for the supported formats,
round-trip guarantees, dimension and metadata loss, indexing behavior, and upgrade steps.

## GML conversion

`geof:asGML(geometry, profile)` converts a supported WKT, GML, or GeoJSON geometry literal to a reusable
`geo:gmlLiteral` without changing the source CRS or its axis semantics. The profile argument must be a simple or
`xsd:string` literal with exactly this value:

```text
http://www.opengis.net/def/profile/ogc/2.0/gml-sf0
```

Non-empty results use Apache Jena's GML 3.2 geometry writer. The supported geometry-fragment subset consists of
Point, LineString, Polygon, MultiPoint, MultiCurve, MultiSurface, and MultiGeometry output. These fragments follow
the corresponding GML 3.2.1 geometry content models; they are not complete GML Simple Features application
documents, and document-level requirements such as `gml:id` remain the responsibility of an application that embeds
the fragment.

XY output is supported. XYZ output requires both an XYZ geometry and a genuinely three-dimensional source CRS.
XYZ geometry under a two-dimensional CRS and measured XYM or XYZM layouts produce a SPARQL expression error. Every
empty input produces the GeoSPARQL zero-length `geo:gmlLiteral` form.

## CRS data

The plugin works out of the box for CRS84/default GeoSPARQL geometry data. The default plugin package does not bundle
the optional Apache SIS `sis-embedded-data` EPSG dataset or national grid-shift files. CRSes outside the small subset
built into Apache SIS require CRS data supplied to GraphDB through `SIS_DATA`.

See [GeoSPARQL CRS Deployment](docs/geosparql-crs-deployment.md) for setup, validation, error handling, and
troubleshooting.
