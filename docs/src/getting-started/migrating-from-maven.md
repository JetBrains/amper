---
description: |
  Migrate your Maven project to Amper: run the semi-automated converter, understand the concept mapping, and learn
  what to do after the conversion.
---

# Migrating from Maven

This section describes how to convert an existing Maven project to Amper.

Amper provides a semi-automated tool to do the bulk of the conversion for you.
It works on a best-effort basis, so some projects require some additional tweaks
after the converter runs.

To run the migration tool, go to the root of your Maven project:

```bash

cd /path/to/your/maven/project

```

Download the [Amper wrapper script](../cli/provisioning.md/#whats-the-wrapper-script).

--8<-- "includes/cli-install.md"

and then run:

```bash
./amper tool convert-project
```

The path to your `pom.xml` can be provided explicitly via `--pom` argument. The `pom.xml` file is a starting point.
If it's a part of the reactor, all related modules are converted.

During the conversion, the tool puts Amper files into corresponding maven modules's folders. If `project.yaml` or one of
the corresponding `module.yaml` files already exists, by default converter fails. To adjust this behavior,
`--overwrite-existing` flag can be used.

## How it works

### Project structure

The converter creates:

- `project.yaml` at the reactor root, with the list of modules
- `module.yaml` in every module directory, both in single-module projects and in multi-module reactor projects

All converted modules use `layout: maven-like`, so your existing `src/main/java`, `src/test/kotlin`, etc. directories
continue to work without moving any files. See [Maven-like layout](../user-guide/advanced/maven-like-layout.md)
for details.

### Dependencies

- **Reactor module dependencies** are converted to relative path dependencies (e.g., `- ../my-lib`)
- **External dependencies** keep their Maven coordinates (`group:artifact:version`)
- **Parent POM BOMs** are automatically imported as `bom:` dependencies (including their transitive parents in order
  from the most root to the most nested)
- **Repositories** (including those inherited from parent POMs) are added to the `repositories` section.
- **Publishing coordinates** (`groupId`, `artifactId`, `version`) are extracted into `settings.publishing`.

### Maven plugin handling

The converter handles Maven plugins in two ways:

**Handled natively** — these plugins cover functionality that Amper provides out of the box.
For some of them, the converter extracts the relevant configuration into Amper settings:

| Maven plugin               | Amper settings                                                                                                                     |
|----------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `maven-compiler-plugin`    | `settings.jvm.release`, `settings.java.freeCompilerArgs`, `settings.java.annotationProcessing`, `settings.jvm.storeParameterNames` |
| `kotlin-maven-plugin`      | `settings.kotlin.*` (version, compiler plugins, args), `settings.jvm.release`                                                      |
| `spring-boot-maven-plugin` | `settings.springBoot`, `product: jvm/app`, `settings.jvm.mainClass`                                                                |
| `maven-surefire-plugin`    | `test-settings.jvm.freeJvmArgs`, `test-settings.jvm.extraEnvironment`, `test-settings.jvm.systemProperties`                        |

Others (`maven-jar-plugin`, `maven-clean-plugin`, `maven-install-plugin`, `maven-source-plugin`, etc.)
don't require any conversion because Amper handles their functionality out of the box.

**Unknown plugins** — all other plugins are downloaded, their descriptors are parsed, and each goal is added to a
`mavenPlugins` section with `enabled: false`. You can enable them selectively after the conversion.

For example, a project using `maven-enforcer-plugin` and `jacoco-maven-plugin` would produce:

```yaml title="module.yaml"
mavenPlugins:
  maven-enforcer-plugin.enforce:
    enabled: false
    configuration:
      rules: |-
        <rules>
          <requireJavaVersion>
            <version>17</version>
          </requireJavaVersion>
        </rules>
  jacoco-maven-plugin.prepare-agent:
    enabled: false
```

The `mavenPlugins` section allows you to run third-party Maven plugins directly in your Amper project.
However, not all plugins are guaranteed to work, so by default they are disabled. You can selectively enable
plugins you need by setting `enabled: true` in their configuration after the conversion.
Please refer to the [Maven plugins](../user-guide/advanced/maven-plugins.md) section for more details.

## Dependency mapping

Maven dependency scopes map to Amper as follows:

| Maven scope         | Amper equivalent                         | Config section      |
|---------------------|------------------------------------------|---------------------|
| `compile` (default) | `compile` + `runtime` + `exported: true` | `dependencies`      |
| `provided`          | `scope: compile-only`                    | `dependencies`      |
| `runtime`           | `scope: runtime-only`                    | `dependencies`      |
| `test`              | `compile` + `runtime`                    | `test-dependencies` |
| `system`            | Not supported                            | —                   |
| `import` (BOM)      | `bom:` prefix                            | `dependencies`      |

!!! note

    The converter marks all `compile`-scoped dependencies as `exported: true`. This is the safe default because
    Maven's `compile` scope makes dependencies transitively visible. After conversion, consider removing `exported`
    from implementation-only dependencies that are not part of your module's public API.
    See [When to use exported](../user-guide/dependencies.md#when-to-use-exported).

Example:

<div class="grid" markdown>

```xml title="pom.xml"

<dependencies>
    <dependency>
        <groupId>io.ktor</groupId>
        <artifactId>ktor-client-core</artifactId>
        <version>3.2.0</version>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <version>2.4.240</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.42</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.12.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

```yaml title="module.yaml"
dependencies:
  - io.ktor:ktor-client-core:3.2.0: exported
  - com.h2database:h2:2.4.240: runtime-only
  - org.projectlombok:lombok:1.18.42: compile-only

test-dependencies:
  - org.junit.jupiter:junit-jupiter:5.12.0
```

</div>

Read more about [Dependencies](../user-guide/dependencies.md).

## After the conversion

After the converter finishes, consider the following steps:

1. **Build and test.** Run `./amper build` and `./amper test` to verify the conversion.

2. **Review generated files.** Check `project.yaml` and each `module.yaml` for correctness.

3. **Review dependency visibility.** The converter marks all `compile`-scoped dependencies as `exported`.
   Remove `exported` from dependencies that are only used internally and are not part of the module's public API.

4. **Review the `mavenPlugins` section.** For unknown plugins:
    - Set `enabled: true` for plugins you actually need.
    - Remove entries for plugins you don't need.
    - Plugin coordinates are listed in `project.yaml` and can be cleaned up as well.

5. **Optional: create a library catalog.** Extract repeated dependency versions into a `libs.versions.toml` file.
   See [Library catalogs](../user-guide/dependencies.md#library-catalogs).

6. **Optional: switch to the standard Amper layout.** The converter sets `layout: maven-like` to avoid moving files.
   If you want to adopt the [standard Amper layout](../user-guide/basics.md#project-layout) later, move files from
   `src/main/kotlin/` to `src/` and from `src/test/kotlin/` to `test/`, then remove the `layout: maven-like` line.
   See [Maven-like layout](../user-guide/advanced/maven-like-layout.md).

## Limitations

The following Maven features are not handled by the converter and require manual migration:

- **Profiles** — Amper does not have an equivalent of Maven profiles. Build configuration can only vary
  [by platform](../user-guide/multiplatform.md).
- **Extensions**
- **Dependency exclusions**
- **Dependency classifiers**
- **Optional dependencies**
- **System-scoped dependencies**
- **Variable substitution** — values are inlined where possible. For dependencies, it's recommended to use a
  [library catalog](../user-guide/dependencies.md#library-catalogs).
