# Basic concepts

## Project and modules

An Amper **project** is defined by a `project.yaml` file. This file contains the list of modules and the project-wide
configuration. The folder with the `project.yaml` file is the project root. Modules can only be located under the 
project root (at any depth). If there is only one module in the project, the `project.yaml` file is not required.

An Amper **module** is a directory with a `module.yaml` configuration file, and optionally sources and resources.
A *module configuration file* describes _what_ to produce: e.g. a reusable library or a platform-specific application.
Each module describes a single product. Several modules can't share the same sources or resources, but they can depend 
on each other.
_How_ to produce the desired product, that is, the build rules, is the responsibility of the Amper build engine.

!!! question "If you are not familiar with YAML, see [our brief YAML primer](yaml-primer.md)."

## Project layout

### Single-module project

A single-module Amper project doesn't need a `project.yaml` file.
Just create a single valid module, and it is also a valid project[^1]:
[^1]: As long as it is not included in a `project.yaml` higher in the directory tree.

```shell title="Single-module project layout"
my-project/ #(1)!
├─ src/
│  ├─ main.kt
├─ test/
│  ╰─ MainTest.kt
╰─ module.yaml
```

1.   This is the project root but also the root of the only module in the project.

See the [Module layout](#module-layout) section for more details about the module structure itself. 

### Multi-module project

If there are multiple modules, the `project.yaml` file specifies the list of modules:

<div class="grid" markdown>
<div class="annotate">
```shell title="Multi-module project layout"
├─ app/
│  ├─ src/
│  │  ├─ main.kt
│  │  ╰─ ...
│  ╰─ module.yaml
├─ libs/ #(1)!
│  ├─ lib1/
│  │  ├─ src/
│  │  │  ╰─ myLib1.kt
│  │  ╰─ module.yaml
│  ╰─ lib2/
│     ├─ src/
│     │  ╰─ myLib2.kt
│     ╰─ module.yaml
╰─ project.yaml
```
</div>

1.   This hierarchy is arbitrary, it can be organized however you like. The structure is understood by Amper based on 
     the list of module paths in `project.yaml` — there is no convention for multi-module projects.

<div class="annotate">
```yaml title="project.yaml"
modules:
  - ./app
  - ./libs/lib1 #(1)!
  - ./libs/lib2
```

```yaml title="app/module.yaml"
product: jvm/app

dependencies:
  - ./libs/lib1
  - ./libs/lib2
```
</div>

1.   It is also possible to use globs to list multiple modules at once (e.g., `./libs/*`), although we encourage 
     listing them explicitly. See details in the [project file reference](../reference/project.md#modules).

</div>

See the [Module layout](#module-layout) section for more details about what goes inside each module directory.

??? note "Multi-module project with root module"

    It is also possible to have a root module even if there are multiple modules in the project, although this is 
    generally discouraged.

    ```shell
    ├─ lib/
    │  ├─ src/
    │  │  ╰─ util.kt
    │  ╰─ module.yaml
    ├─ src/  # src of the root module
    │  ├─ main.kt
    │  ╰─ ...
    ├─ module.yaml  # the module file of the root module
    ╰─ project.yaml
    ```

    ```yaml title="project.yaml"
    modules:  # The root module is included implicitly
      - ./lib
    ```

## Module layout

Here are typical module structures at a glance:

=== ":intellij-java: JVM"

    ```shell
    my-module/
    ├─ resources/ # (1)!
    │  ╰─ logback.xml # (2)!
    ├─ src/
    │  ├─ main.kt
    │  ╰─ Util.java # (3)!
    ├─ test/
    │  ╰─ MainTest.java # (4)!
    │  ╰─ UtilTest.kt
    ├─ testResources/
    │  ╰─ logback-test.xml # (5)!
    ╰─ module.yaml
    ```

    1. Resources placed here are copied into the resulting jar.
    2. This is just an example resource and can be omitted.
    3. You can mix Kotlin and Java source files in a single module, all in the `src` folder.
    4. You can test Java code with Kotlin tests or Kotlin code with Java tests.
    5. This is just an example resource and can be omitted.

    !!! note "Maven compatibility layout for JVM-only modules"
    
        If you're migrating from Maven, you can also configure the [Maven-like layout](advanced/maven-like-layout.md)

=== ":jetbrains-kotlin-multiplatform: Kotlin Multiplatform"

    --8<-- "includes/module-layouts/kmp-lib.md"

    !!! info "Read more in the dedicated [Multiplatform modules](multiplatform.md) section."

=== ":android-head-flat: Android app"

    --8<-- "includes/module-layouts/android-app.md"

    !!! info "Read more in the dedicated [Android](builtin-tech/android.md) section."

=== ":simple-apple: iOS app"

    --8<-- "includes/module-layouts/ios-app.md"

    !!! info "Read more in the dedicated [iOS](builtin-tech/ios.md) section."

All sources and resources are optional: **only the `module.yaml` file is required.**
For example, your module could get all its code from dependencies and have no `src` folder.

Sources and resources can't be defined as part of multiple modules — they must belong to a single module, which other 
modules can depend on. This ensures that the IDE always knows how to analyze and refactor the code, as it always has a 
single well-defined set of settings and dependencies.

## Module file anatomy

A `module.yaml` file has several main sections: `product`, `dependencies` and `settings`.

A module can produce a single product, such as a reusable library or an application.
Read more on the [supported product types](#product-type) below.

Here are some example module files for different types of modules:

=== ":intellij-java: JVM application"

    ```yaml
    product: jvm/app #(1)!
    
    dependencies:
      - io.ktor:ktor-client-java:2.3.0 #(2)!
    
    settings: #(3)!
      kotlin:
        version: 2.2.21 #(4)!
        allWarningsAsErrors: true #(5)!
    ```

    1. This short form is equivalent to:
       ```yaml
        product:
          type: jvm/app
       ```
       The `jvm/app` product type means that the module produces a JVM application.
    2. The `dependencies` section contains the list of dependencies for this module. 
       Here `io.ktor:ktor-client-core:2.3.0` are the 
       [Maven coordinates :fontawesome-solid-external-link:](https://maven.apache.org/pom.html#Maven_Coordinates) of 
       the Ktor client library (with Java engine).
       Read more about dependencies in general in the [Dependencies](dependencies.md) section.
    3. The `settings` section contains the configuration of different toolchains. 
    4. An example setting: the Kotlin compiler version used for this module.
    5. An example setting: a compiler setting to consider warnings as errors and fail the build on any warning.

=== ":jetbrains-kotlin-multiplatform: KMP library"

    ```yaml
    product:
      type: lib #(1)!
      platforms: [android, iosArm64, iosSimulatorArm64] #(2)!
    
    dependencies:
      - io.ktor:ktor-client-core:2.3.0 #(3)!
    
    dependencies@android:
      - io.ktor:ktor-client-android:2.3.0 #(4)!
    
    dependencies@ios:
      - io.ktor:ktor-client-darwin:2.3.0 #(5)!

    settings: #(6)!
      kotlin:
        version: 2.2.21 #(7)!
        allWarningsAsErrors: true #(8)!

    settings@ios: #(9)!
      kotlin:
        allWarningsAsErrors: false #(10)!
    ```

    1. The `lib` product type means that the module produces a :jetbrains-kotlin-multiplatform: Kotlin Multiplatform 
       library.
    2. The `platforms` list contains the platforms that this module is built for.
    3. The `dependencies` section contains the list of common dependencies for this module. 
       Here `io.ktor:ktor-client-core:2.3.0` are the 
       [Maven coordinates :fontawesome-solid-external-link:](https://maven.apache.org/pom.html#Maven_Coordinates) of 
       the Ktor client core library.
       Read more about dependencies in general in the [Dependencies](dependencies.md) section.
       Read more about multiplatform dependencies in the [Multiplatform dependencies](multiplatform.md#multiplatform-dependencies) section.
    4. The `dependencies@android` section contains the list of dependencies that are only used when building the module 
       for the Android target. 
       Here the `io.ktor:ktor-client-android:2.3.0` will not be present when building the module for the iOS targets.
       Read more about dependencies in general in the [Dependencies](dependencies.md) section.
       Read more about multiplatform dependencies in the [Multiplatform dependencies](multiplatform.md#multiplatform-dependencies) section.
    5. The `dependencies@ios` section contains the list of dependencies that are only used when building the module 
       for the iOS target. 
       Here the `io.ktor:ktor-client-darwin:2.3.0` will not be present when building the module for the Android target.
       Read more about dependencies in general in the [Dependencies](dependencies.md) section.
       Read more about multiplatform dependencies in the [Multiplatform dependencies](multiplatform.md#multiplatform-dependencies) section.
    6. The `settings` section contains the configuration of different toolchains for common code, and also serves as 
       default for platform-specific code.. 
    7. An example setting: the Kotlin compiler version used for this module.
    8. An example setting: a compiler setting to consider warnings as errors and fail the build on any warning.
    9. The `settings@ios` section contains the configuration of different toolchains for iOS-specific compilation.
    10. This setting overrides the one that we set in the `settings` section.
        Read more about this in the [settings propagation](multiplatform.md#multiplatform-settings) section.

### Product type

The **product type** describes the target platform and the type of the module at the same time. Below is the list of 
supported product types:

- `lib` - a reusable Kotlin Multiplatform library which can be used as a dependency by other modules in the project
- `jvm/lib` - a reusable JVM library which can be used as a dependency by other modules in the project
- `jvm/app` - a JVM console or desktop application
- `windows/app` - a mingw64 application
- `linux/app` - a native Linux application
- `macos/app` - a native macOS application
- `android/app` - an [Android](builtin-tech/android.md) application
- `ios/app` - an [iOS](builtin-tech/ios.md) application

### Dependencies

The `dependencies` section contains the list of dependencies for this module.
They can be external maven libraries, other modules in the project, and more.

Please see the [Dependencies](dependencies.md) section for more details.

### Settings

The `settings` section contains toolchains settings.
A _toolchain_ is an SDK (Kotlin, Java, Android, iOS) or a simpler tool (linter, code generator).

All toolchain settings are specified in dedicated groups in the `settings` section:
```yaml
settings:
  kotlin:
    languageVersion: 1.8
  android:
    compileSdk: 31
```

Check out the [Reference](../reference/module.md#settings-and-test-settings) page for the full list of supported settings.

See the [multiplatform section](multiplatform.md) for more details about how multiple settings sections interact in
multiplatform modules.

#### Configuring entry points

##### JVM

By default, the entry point of JVM applications (the `main` function) is expected to be in a `main.kt` file
(case-insensitive) in the `src` folder.

This can be overridden by specifying a main class explicitly in the module settings:
```yaml
product: jvm/app

settings:
  jvm:
    mainClass: org.example.myapp.MyMainKt
```

!!! note

    In Kotlin, unlike Java, the `main` function doesn't have to be declared in a class, and is usually at the top level
    of the file. However, the JVM still expects a main class when running any application. Kotlin always compiles 
    top-level declarations to a class, and the name of that class is derived from the name of the file by capitalizing 
    the name and turning the `.kt` extension into a `Kt` suffix.
    
    For example, the top-level declarations of `myMain.kt` will be in a class named `MyMainKt`.

##### Native

By default, the entry point of Kotlin native applications (the `main` function) is expected to be in a `main.kt` file
(case-insensitive) in the `src` folder.

This can be overridden by specifying the fully qualified name of the `main` function explicitly in the module settings:

```yaml
product: linux/app

settings:
  native:
    entryPoint: org.example.myapp.main
```

##### Android

Android apps have their own way to configure the entry point, see the 
[dedicated Android section](builtin-tech/android.md#entry-point).

##### iOS

iOS apps have their own way to configure the entry point, see the
[dedicated iOS section](builtin-tech/ios.md#application-entry-point).

## Packaging

Amper provides a `package` command to build a project for distribution.

For `jvm/app` modules it produces executable jars which follow 
[The Executable Jar Format](https://docs.spring.io/spring-boot/specification/executable-jar/index.html).
The executable JAR format, while commonly associated with Spring applications, is a universal packaging solution
suitable for any JVM application. This format provides a convenient, runnable self-contained deployment unit that
includes all necessary dependencies, but unlike the "fat jar" approach, it doesn't suffer from the problems of handling
duplicate files.

For `android/app` modules, see the dedicated [Android packaging](builtin-tech/android.md#packaging) section.

