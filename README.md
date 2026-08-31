# GraphDB GeoSPARQL Plugin

The GraphDB GeoSPARQL Plugin adds indexed GeoSPARQL predicates and SPARQL functions for querying and transforming
geometry data in GraphDB 11. It supports WKT, GML, and GeoJSON literals, spatial relationships, measurements,
coordinate transformations, and geometry conversion.

Install the plugin using the steps below, then follow [Using the GeoSPARQL plugin](docs/geosparql-usage.md) to enable
it for a repository and run your first indexed query.

For GeoSPARQL documentation for the latest GraphDB release, see
[GraphDB GeoSPARQL support](http://graphdb.ontotext.com/documentation/enterprise/geosparql-support.html).

## Compatibility and prerequisites

The plugin targets GraphDB 11 and is compiled for Java 21. JDK 21 is required to build the plugin and run it in
GraphDB.

CRS84 geometry data works without additional CRS configuration. Projected and other EPSG CRSs may require Apache SIS
CRS data and grid files supplied to GraphDB through `SIS_DATA`. See
[GeoSPARQL CRS deployment](docs/geosparql-crs-deployment.md) for setup and validation.

## Installing a locally built package

Build the plugin as described under [Building from source](#building-from-source), then install the generated archive:

1. Stop GraphDB.
2. If upgrading an existing installation, remove `$GDB_HOME/lib/plugins/geosparql-plugin`. Also remove
   `$GDB_HOME/lib/plugins/graphdb-geosparql-plugin` if it exists from an older installation.
3. Extract `target/geosparql-plugin-graphdb-plugin.zip` into `$GDB_HOME/lib/plugins`. This creates the
   `$GDB_HOME/lib/plugins/geosparql-plugin` directory.
4. Start GraphDB.
5. Follow [Using the GeoSPARQL plugin](docs/geosparql-usage.md) to enable the plugin for each repository that needs
   indexed GeoSPARQL predicates.

## Documentation

- [Using the GeoSPARQL plugin](docs/geosparql-usage.md) — quick start, configuration, index controls, rebuilding, and
  error handling.
- [GeoSPARQL functions and predicates reference](docs/geosparql-functions-and-predicates.md) — supported functions,
  signatures, indexed predicates, geometry data-model properties, and GraphDB extensions.
- [Geometry serialization and conversion](docs/geosparql-geometry-serialization.md) — WKT, GML, and GeoJSON formats,
  conversion rules, dimensions, metadata, and indexing behavior.
- [GeoSPARQL CRS deployment](docs/geosparql-crs-deployment.md) — Apache SIS data, projected CRS support, validation,
  and troubleshooting.

## Building from source

The plugin is a Maven project. With JDK 21 and Maven installed, run:

```bash
mvn clean package
```

The build executes the test suite and creates `target/geosparql-plugin-graphdb-plugin.zip`. It also checks that the
archive contains the plugin and supported Apache SIS Derby runtime while excluding test-only and legacy CRS provider
dependencies.

The tests use the test-scoped Apache SIS `sis-embedded-data` dependency, whose EPSG data is subject to the
[EPSG Terms of Use](https://epsg.org/terms-of-use.html). This dependency is not included in the plugin archive.

## Development packaging test

The optional packaging smoke test requires Docker. It installs the assembled archive into a temporary GraphDB
container and checks CRS84 and projected-CRS indexed predicates together with a projected-CRS distance function:

```bash
mvn -Pgraphdb-packaging-smoke verify
```

The smoke test uses GraphDB 10.8.12 as its unattended test host; this does not establish GraphDB 10.8 compatibility.
The production target remains GraphDB 11. See the
[repository packaging smoke test](docs/geosparql-crs-deployment.md#repository-packaging-smoke-test) for its external
Apache SIS data requirements.

## License

This project is licensed under the [Apache License 2.0](LICENSE).
