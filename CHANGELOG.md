## 0.3.1-dev-581

* Standalone version of Amper. See the [usage instructions](docs/Usage.md#using-the-standalone-amper-version-from-the-command-line).
  * Build, test, and run JVM and Android applications in CLI and Fleet.
  * iOS support is on the way.
  * Standalone Amper can automatically download and configure the build environment, such as JDK, Android SDK.
* Gradle-based Amper has been updated with the following plugins:
  * Kotlin 2.0
  * Compose 1.6.10
  * Android 8.2
* Gradle 8.6 is recommended for the Gradle-based Amper. Gradle 8.7+ is not yet supported. 
* Support a Wasm platform for libraries. 

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