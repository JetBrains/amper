## 0.4.0

See our [release blog post](TODO_INSERT_URL_HERE______________IT_WILL_PROBABLY_BE_THIS_LONG) for the highlights, and to
learn about the tooling improvements.

### Breaking changes

* [AMPER-760](https://youtrack.jetbrains.com/issue/AMPER-760) Amper is now published to a new Maven repository.
  This affects the Gradle plugin, as well as the standalone Amper distribution and executables:
  * Old repository URL: `https://maven.pkg.jetbrains.space/public/p/amper/amper`
  * New repository URL: `https://packages.jetbrains.team/maven/p/amper/amper`

  See [Usage.md](docs/Usage.md) for instructions on how to update your project.

* [AMPER-393](https://youtrack.jetbrains.com/issue/AMPER-393) Amper modules are no longer automatically discovered by
  searching the whole file system hierarchy.
  The [documentation](docs/Documentation.md) has been updated accordingly.
  * In Gradle-based Amper projects, modules with a `module.yaml` file now need to be listed alongside other Gradle
    modules in `settings.gradle(.kts)`
  * In multi-module projects using standalone Amper, a `project.yaml` file is now required and must list the modules
    to include in the project. Nothing changes for single-module projects.

### Enhancements


* [AMPER-685](https://youtrack.jetbrains.com/issue/AMPER-685) User-friendly error message when a task doesn't exist
* [AMPER-717](https://youtrack.jetbrains.com/issue/AMPER-717) Fail fast if the Android SDK license isn't accepted
* [AMPER-790](https://youtrack.jetbrains.com/issue/AMPER-790) Inspection about incompatible settings or settings under an incorrect section

### Bug fixes

* [AMPER-494](https://youtrack.jetbrains.com/issue/AMPER-494) 'buildSrc' cannot be used as a project name as it is a reserved name
* [AMPER-624](https://youtrack.jetbrains.com/issue/AMPER-624) Compilation fails with language version 2.0
* [AMPER-689](https://youtrack.jetbrains.com/issue/AMPER-689) Fail to resolve kotlin-test-annotations-common in a multiplatform context
* [AMPER-836](https://youtrack.jetbrains.com/issue/AMPER-836) Support repositories basic authentication in DR

## 0.3.1 (2024-06-06)

* [AMPER-625](https://youtrack.jetbrains.com/issue/AMPER-625) Update Kotlin to 2.0 release
* [AMPER-628](https://youtrack.jetbrains.com/issue/AMPER-628) Update Compose to 1.6.10 release
* [AMPER-620](https://youtrack.jetbrains.com/issue/AMPER-620) Update Apple Gradle plugin to include the latest fixes (222.4595-0.23.2)
* [AMPER-443](https://youtrack.jetbrains.com/issue/AMPER-443) Compose doesn't work with Gradle 8.6+
* Support Gradle > 8.6 for the _bundled_ Compose version (and improve error message for other versions).

## 0.3.0 (2024-05-28)

See our [release blog post](https://blog.jetbrains.com/amper/2024/05/amper-update-may-2024/) for the highlights, and to
learn about the tooling improvements.

* Standalone version of Amper. See the [usage instructions](docs/Usage.md#using-the-standalone-amper-version-from-the-command-line).
  * Build, test, and run JVM and Android applications in CLI and Fleet.
  * iOS support is on the way.
  * Standalone Amper can automatically download and configure the build environment, such as JDK, Android SDK.
* Gradle-based Amper has been updated with the following plugins:
  * Kotlin 2.0.0-RC3
  * Compose 1.6.10-rc01
  * Android 8.2
* Gradle 8.6 is recommended for the Gradle-based Amper. Gradle 8.7+ is not yet supported. 
* Support the Wasm platform for libraries. 

### Breaking changes

Amper now uses the `release` javac option instead of `target` and `source` to compile the Java code. 
This version is also passed to the Kotlin compiler on the JVM platform.   

You need to update your configuration files. If you have the following settings:

```yaml
settings:
  java:
    source: 17
  jvm:
    target: 17
```

Remove `java:source:` and change `jvm:target:` to `jvm:release:`: 
```yaml
settings:
  jvm:
    release: 17
```

See the [DSL reference](docs/DSLReference.md#jvm) for details.