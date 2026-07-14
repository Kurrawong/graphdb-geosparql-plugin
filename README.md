# GraphDB GeoSPARQL Plugin

This is the GraphDB GeoSPARQL plugin. More information about it is available in the GraphDB documentation here:
http://graphdb.ontotext.com/documentation/enterprise/geosparql-support.html

## Building the plugin

The plugin is a Maven project.

Run `mvn clean package` to build the plugin and execute the tests.

The built plugin can be found in the `target` directory:

- `graphdb-geosparql-plugin-graphdb-plugin.zip`

### Packaging smoke test

The opt-in packaging smoke test requires Docker. It builds a temporary GraphDB 10.8.12 image with Java 21, installs
the assembled plugin ZIP, and runs one indexed GeoSPARQL property-relation query:

```bash
mvn -Pgraphdb-packaging-smoke verify
```

This test checks the plugin archive layout and runtime dependency closure. It does not establish GraphDB 10.8 support
or replace validation against the target GraphDB 11 runtime.

## Installing the plugin

External plugins are installed under `lib/plugins` in the GraphDB distribution
directory. To install the plugin follow these steps:

1. Remove the directory containing another version of the plugin from `lib/plugins` (e.g. `graphdb-geosparql-plugin`).
1. Unzip the built zip file in `lib/plugins`.
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

## CRS data

The plugin works out of the box for CRS84/default GeoSPARQL geometry data. The default plugin package does not bundle
non-free EPSG data or national grid-shift files.

Default supported behavior:

- WKT literals without an explicit SRS URI use the GeoSPARQL default CRS, CRS84:
  `http://www.opengis.net/def/crs/OGC/1.3/CRS84`.
- WKT/GML literals that explicitly use CRS84 can be indexed and evaluated without extra Apache SIS data.
- CRS84-derived index geometry is written to Lucene for candidate lookup.
- Exact evaluation uses the source geometry literal and the CRS operations available to Apache SIS.

Other CRS support without configuration is inherited from Apache Jena and Apache SIS runtime defaults. Apache Jena
documents that CRS conversion depends on the local Apache SIS EPSG dataset, and Apache SIS documents that without the
EPSG geodetic dataset only a small CRS subset is available and coordinate operations may be less accurate or have
unspecified domains of validity. This plugin's certified default CRS support is CRS84. Projected CRSes, EPSG CRSes
beyond CRS84, national grids, and datum-shift requirements depend on the Apache SIS data available in the GraphDB
runtime.

Useful upstream references:

- Apache Jena GeoSPARQL: https://jena.apache.org/documentation/geosparql/
- Apache SIS EPSG dataset setup: https://sis.apache.org/epsg.html

### Configuring Apache SIS data

To make Apache SIS data available to GraphDB, set the `SIS_DATA` environment variable to a readable Apache SIS data
directory before starting GraphDB. The environment variable must be visible to the JVM that runs GraphDB. The directory
contents are managed by Apache SIS, not by this plugin.

### Startup logging

The plugin does not validate arbitrary CRSes at startup and does not fail startup when `SIS_DATA` is missing. Startup
logging is informational and reports only the Apache SIS data environment visible to GraphDB.

If `SIS_DATA` is unset, the plugin logs an informational message like:

```text
GeoSPARQL CRS data: SIS_DATA is not set. CRS84/default GeoSPARQL data is supported. Projected/EPSG CRS data may require Apache SIS data and grid files; unsupported CRS will fail indexing/query evaluation.
```

If `SIS_DATA` is set to a readable directory, the plugin logs an informational message like:

```text
GeoSPARQL CRS data: SIS_DATA=/opt/graphdb/sis-data is a readable directory. Projected/EPSG CRS support depends on the CRS definitions and grid files available in that directory. Unsupported CRS will fail indexing/query evaluation.
```

If `SIS_DATA` is set but unusable, the plugin logs a warning like:

```text
GeoSPARQL CRS data: SIS_DATA=/opt/graphdb/sis-data is not readable. CRS84/default GeoSPARQL data is supported, but CRS data from SIS_DATA will not be available. Unsupported CRS will fail indexing/query evaluation.
```

The plugin does not create directories, inspect CRS definitions, download data, validate EPSG codes, or block startup
based on `SIS_DATA`.

### Unsupported CRS behavior

`ignoreErrors` is `false` by default.

When GeoSPARQL is enabled and repository data contains a geometry whose CRS cannot be parsed, resolved, or transformed
with the deployed CRS data, indexing fails by default with an actionable error. This can happen during incremental
indexing after inserts or during a full reindex.

If `ignoreErrors=true`, invalid or unsupported repository geometries are skipped during indexing with a warning. This
does not make those geometries queryable.

`ignoreErrors` does not apply to query-supplied literals. If a SPARQL query supplies a geometry literal with an
unsupported CRS, query evaluation fails rather than silently treating the coordinates as CRS84 or WGS84.

If the plugin is disabled while data is loaded, unsupported CRS failures are deferred until the plugin is enabled or the
GeoSPARQL index is rebuilt.
