# Basic concepts

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

```shell title="Single-module project structure"
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
```shell title="Structure"
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

### Overview

A typical Amper module looks like this:

```shell title="Module directory structure"
my-project/
├─ resources/ # (1)!
│  ╰─ logback.xml  # (2)!
├─ src/
│  ├─ main.kt
│  ╰─ MyClass.java  # (3)!
├─ test/
│  ╰─ MainTest.kt
├─ testResources/
│  ╰─ logback-test.xml # (4)!
╰─ module.yaml
```

1. Resources placed here are copied to the resulting products (e.g., the jar or APK).
2. This is just an example resource and can be omitted.
3. You can mix Kotlin and Java source files in a single module, all in the `src` folder.
4. This is just an example resource and can be omitted.

All sources and resources are optional: **only the `module.yaml` file is required.**
For example, your module could get all its code from dependencies and have no `src` folder.

!!! info "Special layouts"

    Some modules have a special layout described in their dedicated sections:

    * [Multiplatforms modules](multiplatform.md) (special `@platform` suffixes)
    * [Android modules](builtin-tech/android.md) (`res` and `assets` folders)
    * Modules that use the [Maven-like layout](advanced/maven-like-layout.md)

*[Android modules]: That is, modules with `android/app` product type.

### Source code

Source files are located in the `src` folder:

```
├─ src/
│  ╰─ main.kt
╰─ module.yaml
```

By convention, a `main.kt` file, if present, is the default entry point for the application.
Read more on [configuring the application entry points](#configuring-entry-points).

In a JVM module, you can mix Kotlin and Java source files:

```
├─ src/
│  ├─ main.kt
│  ╰─ Util.java
╰─ module.yaml
```

In a [multiplatform module](multiplatform.md), platform-specific code is located in folders
with [`@platform`-qualifiers](multiplatform.md#platform-qualifier):

```
├─ src/             # common code
│  ├─ main.kt
│  ╰─ util.kt       #  API with ‘expect’ part
├─ src@ios/         # code to be compiled only for iOS targets
│  ╰─ util.kt       #  API implementation with ‘actual’ part for iOS
├─ src@jvm/         # code to be compiled only for JVM targets
│  ╰─ util.kt       #  API implementation with ‘actual’ part for JVM
╰─ module.yaml
```

Sources and resources can't be shared by multiple modules. This ensures that the IDE always knows how to analyze and
refactor the code, as it always exists in the scope of a single module, has a well-defined list of dependencies, etc.

### Resources

Files placed into the `resources` folder are copied to the resulting products:

```
├─ src/
│  ╰─ ...
╰─ resources/     # These files are copied into the final products
   ╰─ ...
```

In [multiplatform modules](#multiplatform-configuration), resources are merged from the common folders and corresponding
platform-specific folders:
```
├─ src/
│  ╰─ ...
├─ resources/          # these resources are copied into the Android and JVM artifact
│  ╰─ ...
├─ resources@android/  # these resources are copied into the Android artifact
│  ╰─ ...
╰─ resources@jvm/      # these resources are copied into the JVM artifact
   ╰─ ...
```

In case of duplicated names, the common resources are overwritten by the more specific ones.
That is `resources/foo.txt` will be overwritten by `resources@android/foo.txt`.

Android modules also have [`res` and `assets`](https://developer.android.com/guide/topics/resources/providing-resources) 
folders:
*[Android modules]: That is, modules with `android` product type.

```
├─ src/
│  ╰─ ...
├─ res/
│  ├─ drawable/
│  │  ╰─ ...
│  ├─ layout/
│  │  ╰─ ...
│  ╰─ ...
├─ assets/
│  ╰─ ...
╰─ module.yaml
```

## Module file anatomy

A `module.yaml` file has several main sections: `product:`, `dependencies:` and `settings:`. A module can produce a
single product, such as a reusable library or an application.
Read more on the [supported product types](#product-types) below.

Here is an example of a JVM console application with a single dependency and a specified Kotlin language version:
```yaml
product: jvm/app

dependencies:
  - io.ktor:ktor-client-core:2.3.0

settings:
  kotlin:
    languageVersion: 1.9
```

Example of a KMP library:
```yaml
product: 
  type: lib
  platforms: [android, iosArm64]

settings:
  kotlin:
    languageVersion: 1.9
```

### Product types

Product type describes the target platform and the type of the project at the same time. Below is the list of supported
product types:

- `lib` - a reusable Kotlin Multiplatform library which can be used as a dependency by other modules in the Amper project
- `jvm/lib` - a reusable JVM library which can be used as a dependency by other modules in the Amper project
- `jvm/app` - a JVM console or desktop application
- `windows/app` - a mingw64 application
- `linux/app` - a native linux application
- `macos/app` - a native macOS application
- `android/app` - an Android VM application
- `ios/app` - an iOS/iPadOS application

### Multiplatform configuration

`dependencies:` and `setting:` sections can be specialized for each platform using the `@platform`-qualifier.
An example of a multiplatform library with some common and platform-specific code:
```yaml
product:
  type: lib
  platforms: [iosArm64, android]

# These dependencies are available in common code.
# They are also propagated to iOS and Android code, along with their platform-specific counterparts 
dependencies:
  - io.ktor:ktor-client-core:2.3.0

# These dependencies are available in Android code only
dependencies@android:
  - io.ktor:ktor-client-android:2.3.0
  - com.google.android.material:material:1.5.0

# These dependencies are available in iOS code only
dependencies@ios:
  - io.ktor:ktor-client-darwin:2.3.0

# These settings are for common code.
# They are also propagated to iOS and Android code 
settings:
  kotlin:
    languageVersion: 1.8
  android:
    compileSdk: 33

# We can add or override settings for specific platforms. 
# Let's override the Kotlin language version for iOS code: 
settings@ios:
  kotlin:
    languageVersion: 1.9 
```
See details on multiplatform configuration in the dedicated [multiplatform](multiplatform.md) section.

### Settings

The `settings:` section contains toolchains settings.
A _toolchain_ is an SDK (Kotlin, Java, Android, iOS) or a simpler tool (linter, code generator).

All toolchain settings are specified in dedicated groups in the `settings:` section:
```yaml
settings:
  kotlin:
    languageVersion: 1.8
  android:
    compileSdk: 31
```

Here is the list of [currently supported toolchains and their settings](../reference/module.md#settings-and-test-settings).

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
product: jvm/app

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

