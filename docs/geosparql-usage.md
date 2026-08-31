# Using the GeoSPARQL plugin

The GeoSPARQL plugin is configured separately for each GraphDB repository through SPARQL updates. Enable it to build
and maintain a spatial index for GeoSPARQL predicate queries. GeoSPARQL functions do not use the index and remain
available when the plugin is disabled.

## Quick start

### Add geometry data

Describe a Feature, its default Geometry, and the Geometry's serialization with GeoSPARQL properties:

```sparql
PREFIX ex: <http://example.com/>
PREFIX geo: <http://www.opengis.net/ont/geosparql#>

INSERT DATA {
  ex:place a geo:Feature ;
      geo:hasDefaultGeometry ex:placeGeometry .

  ex:placeGeometry a geo:Geometry ;
      geo:asWKT "POINT(153.0251 -27.4698)"^^geo:wktLiteral .
}
```

The plugin also discovers geometry literals supplied with `geo:asGML` and `geo:asGeoJSON`.

### Enable the plugin

The plugin is disabled by default. Enable it for the repository with this SPARQL update:

```sparql
PREFIX plugin: <http://www.ontotext.com/plugins/geosparql#>

INSERT DATA {
  [] plugin:enabled true .
}
```

Enabling the plugin builds an index from existing geometry data. After the index is built, changes to supported
GeoSPARQL geometry statements are reflected in the index automatically. Building the index may take time for a large
repository.

### Run an indexed spatial query

Use a GeoSPARQL predicate to find indexed Features or Geometries. This example finds Features within a query polygon:

```sparql
PREFIX geo: <http://www.opengis.net/ont/geosparql#>

SELECT ?feature
WHERE {
  VALUES ?searchArea {
    "POLYGON((152 -28, 154 -28, 154 -26, 152 -26, 152 -28))"^^geo:wktLiteral
  }

  ?feature a geo:Feature ;
      geo:sfWithin ?searchArea .
}
```

The bound query polygon in this example is a GraphDB query convenience. GeoSPARQL defines relation properties between
`geo:SpatialObject` resources, so queries intended to be portable should use Feature or Geometry resources as
predicate operands.

The predicate form uses the spatial index to find possible matches and then verifies the relationship. Use the
corresponding `geof:` function when the query already has both geometry literals and does not need an indexed search.
See the [GeoSPARQL functions and predicates reference](geosparql-functions-and-predicates.md) for the available query
operations.

## Configuration parameters

The following predicates read or change the repository's GeoSPARQL configuration:

| Predicate | Purpose | Default and accepted values |
| --- | --- | --- |
| `plugin:enabled` | Enables or disables the spatial index and GeoSPARQL predicate queries. | `false` |
| `plugin:prefixTree` | Selects the spatial prefix tree used by the index. | `quad`; accepts `quad` or `geohash` |
| `plugin:precision` | Sets the number of prefix-tree levels used by the index. | `11`; `1`–`50` for `quad` and `1`–`24` for `geohash` |
| `plugin:currentPrefixTree` | Reports the prefix tree used by the current index. This value is read-only. | `quad` before the first configuration change |
| `plugin:currentPrecision` | Reports the precision used by the current index. This value is read-only. | `11` before the first configuration change |
| `plugin:maxBufferedDocs` | Sets how many index documents may be buffered before they are written. | `1000`; accepts `1`–`5000` |
| `plugin:ramBufferSizeMB` | Sets the index writer's memory threshold in megabytes. | `32.0`; accepts `16.0`–`512.0` |
| `plugin:ignoreErrors` | Controls whether invalid or unsupported geometry data is skipped while indexing. | `false` |

`plugin:prefixTree` and `plugin:precision` show the requested settings. Their `plugin:currentPrefixTree` and
`plugin:currentPrecision` counterparts show the settings used to build the current index. If the plugin is enabled,
force a reindex after changing the requested prefix tree or precision. If the plugin is disabled, enabling it builds
the index with the requested settings, so a separate force reindex is unnecessary.

Precision controls index selectivity and size. Spatial relationships are still verified against the source geometry
literals after candidate lookup.

## Plugin control predicates

Use the `http://www.ontotext.com/plugins/geosparql#` control predicates in SPARQL updates and queries. These controls
change or report plugin configuration; they are not geometry data stored in the repository.

### Disable the plugin

```sparql
PREFIX plugin: <http://www.ontotext.com/plugins/geosparql#>

INSERT DATA {
  [] plugin:enabled false .
}
```

While disabled, the plugin does not incrementally index geometry-data updates or answer indexed GeoSPARQL predicate
queries. GeoSPARQL functions and plugin configuration controls remain available.

### Check the current configuration

The configuration is exposed through the plugin control resource:

```sparql
SELECT ?setting ?value
WHERE {
  <http://www.ontotext.com/plugins/geosparql> ?setting ?value .
}
ORDER BY ?setting
```

### Change index settings

Set a prefix tree and precision with a SPARQL update:

```sparql
PREFIX plugin: <http://www.ontotext.com/plugins/geosparql#>

INSERT DATA {
  [] plugin:prefixTree "quad" ;
     plugin:precision 20 .
}
```

If the plugin is enabled, run the update below to rebuild the index after changing these settings. If the plugin is
disabled, enable it to build the index with the requested settings.

### Rebuild the index

Rebuilding replaces the spatial index with one generated from the repository's current geometry data and applies the
requested prefix tree and precision:

```sparql
PREFIX plugin: <http://www.ontotext.com/plugins/geosparql#>

INSERT DATA {
  [] plugin:forceReindex true .
}
```

The plugin must already be enabled. The update waits for the rebuild to finish and may take significant time for a
large repository.

### Ignore invalid geometry data while indexing

By default, invalid geometry data or an unsupported CRS stops an index build or repository update. To skip affected
geometries and continue indexing, set `plugin:ignoreErrors` to `true`:

```sparql
PREFIX plugin: <http://www.ontotext.com/plugins/geosparql#>

INSERT DATA {
  [] plugin:ignoreErrors true .
}
```

The setting applies to subsequent indexing work. Rebuild the GeoSPARQL index after changing it if existing repository
geometries need to be reconsidered under the new policy.

Skipped geometries are not available to indexed predicate queries. Query-supplied geometry literals still produce
an error when they are invalid or use an unsupported CRS. See
[GeoSPARQL CRS deployment](geosparql-crs-deployment.md#loading-data-with-unsupported-crs) for details.

### Tune index building

Larger buffer values can improve index-building throughput but use more memory:

```sparql
PREFIX plugin: <http://www.ontotext.com/plugins/geosparql#>

INSERT DATA {
  [] plugin:maxBufferedDocs 3000 ;
     plugin:ramBufferSizeMB 256.0 .
}
```

Keep both values within the ranges in the configuration table. Excessive buffering can increase memory pressure and
index-merging work.

## Related documentation

- [GeoSPARQL functions and predicates reference](geosparql-functions-and-predicates.md)
- [Geometry serialization and conversion](geosparql-geometry-serialization.md)
- [GeoSPARQL CRS deployment](geosparql-crs-deployment.md)
