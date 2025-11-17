# Dependencies

## External Maven dependencies

Maven dependencies can be added via their coordinates[^1] using the usual `:`-separated notation:

```yaml
dependencies:
  - org.jetbrains.kotlin:kotlin-serialization:1.8.0
  - io.ktor:ktor-client-core:2.2.0
```

[^1]: If you're not familiar with Maven coordinates, check out the 
[Maven documentation :fontawesome-solid-external-link:](https://maven.apache.org/pom.html#Maven_Coordinates).

## Module dependencies

To depend on another module of your project, use a relative path to the folder which contains the corresponding 
`module.yaml`.
The path should start either with `./` or `../`.

!!! note

    Dependencies between modules are only allowed within the project scope.
    That is, they must be listed in the `project.yaml` file and cannot be outside the project root directory.

Example: given the project layout

```
root/
├─ app/
│  ├─ src/
│  ╰─ module.yaml
╰─ ui/
   ╰─ utils/
      ├─ src/
      ╰─ module.yaml
```

The `app/module.yaml` can declare a dependency on `ui/utils` as follows:

```yaml
dependencies:
  - ../ui/utils
```

Other examples of the internal dependencies:

```yaml
dependencies:
  - ./nested-folder-with-module-yaml
  - ../sibling-folder-with-module-yaml
```

## Scopes and visibility

There are three dependency scopes:

- `all` - (default) the dependency is available during compilation and runtime.
- `compile-only` - the dependency is only available during compilation. This is a 'provided' dependency in Maven terminology.
- `runtime-only` - the dependency is not available during compilation, but available during testing and running

In a full form you can declare scope as follows:

```yaml
dependencies:
  - io.ktor:ktor-client-core:2.2.0:
      scope: compile-only 
  - ../ui/utils:
      scope: runtime-only 
```

There is also an inline form:

```yaml
dependencies:
  - io.ktor:ktor-client-core:2.2.0: compile-only  
  - ../ui/utils: runtime-only
```

All dependencies by default are not accessible from the dependent code.  
In order to make a dependency visible to a dependent module, you need to explicitly mark it as `exported` (this is
equivalent to declaring a dependency using the `api()` configuration in Gradle).

```yaml
dependencies:
  - io.ktor:ktor-client-core:2.2.0:
      exported: true 
  - ../ui/utils:
      exported: true 
```

There is also an inline form:

```yaml
dependencies:
  - io.ktor:ktor-client-core:2.2.0: exported
  - ../ui/utils: exported
```

Here is an example of a `compile-only` and `exported` dependency:

```yaml
dependencies:
  - io.ktor:ktor-client-core:2.2.0:
      scope: compile-only
      exported: true
```

## BOM dependencies

To import a BOM (Bill of materials), specify its coordinates prefixed by `bom: `

```yaml
dependencies:
  - bom: io.ktor:ktor-bom:2.2.0
  - io.ktor:ktor-client-core 
```

After a BOM is imported, the versions of the dependencies declared in the module can be omitted,
unspecified versions are resolved from the BOM.
Dependency versions declared in the BOM participate in version conflict resolution.

## Library Catalogs (a.k.a Version Catalogs)

A library catalog associates keys to library coordinates (including the version), and allows adding the same libraries
as dependencies to multiple modules without having to repeat the coordinates or the versions of the libraries.

Amper currently supports 2 types of dependency catalogs:

- toolchain catalogs (such as Kotlin, Compose Multiplatform etc.)
- [Gradle version catalogs in TOML format](https://docs.gradle.org/current/userguide/version_catalogs.html#sec:version-catalog-declaration) 
  that are placed in the default `gradle/libs.versions.toml` location or in `libs.versions.toml` at the root of the project.

The toolchain catalogs are implicitly defined, and contain predefined libraries that relate to the corresponding toolchain.
The name of such a catalog corresponds to the name of the corresponding toolchain in the [settings section](basics.md#settings).
For example, dependencies for the Compose Multiplatform frameworks are accessible using the `$compose` catalog.
All dependencies in such catalogs usually have the same version, which is the toolchain version.

The Gradle version catalogs are user-defined catalogs using the Gradle format.
Dependencies from this catalog can be accessed via the `$libs` catalog, and the library keys are defined according
to the [Gradle name mapping rules](https://docs.gradle.org/current/userguide/version_catalogs.html#sec:mapping-aliases-to-accessors).

To use dependencies from catalogs, use the syntax `$<catalog-name>.<key>` instead of the coordinates, for example:
```yaml
dependencies:
  - $kotlin.reflect      # dependency from the Kotlin catalog
  - $compose.material3   # dependency from the Compose Multiplatform catalog
  - $libs.commons.lang3  # dependency from the Gradle default libs.versions.toml catalog
```

Catalog dependencies can still have a [scope and visibility](#scopes-and-visibility) even when coming from a catalog:

```yaml
dependencies:
  - $compose.foundation: exported
  - $my-catalog.db-engine: runtime-only 
```

## Managing Maven repositories

By default, Maven Central and Google Android repositories are pre-configured. To add extra repositories, use the following options:

```yaml
repositories:
  - https://repo.spring.io/ui/native/release
  - url: https://dl.google.com/dl/android/maven2/
  - id: jitpack
    url: https://jitpack.io
```

To configure repository credentials, use the following snippet:
```yaml
repositories:
  - url: https://my.private.repository/
    credentials:
      file: ../local.properties # relative path to the file with credentials
      usernameKey: my.private.repository.username
      passwordKey: my.private.repository.password
```

Here is the file `../local.properties`:
```properties
my.private.repository.username=...
my.private.repository.password=...
```

!!! note

    Currently only `*.properties` files with credentials are supported.
