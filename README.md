[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Latest release](https://img.shields.io/github/v/tag/JetBrains/amper?color=brightgreen&label=latest%20release)](https://github.com/JetBrains/amper/tags)

# Amper

Amper is a project configuration and build tool. Its goal is to provide a great and smooth user experience and IDE support 
We believe that this can be achieved by:

- providing a developer- and IDE-friendly declarative configuration DSL - to simplify not only the initial setup but
  also improve maintainability and let an IDE assist with automatic configuration reliably;
- bundling a curated set of compatible toolchains and extensions - to support the majority of the scenarios without the need to find compatible plugins;
- carefully choosing the extensibility points - to keep the overall mental model and UX of the configuration consistent and to avoid unexpected third-party code execution.

In essence, we aim to achieve a similar well-thought-out and well-tested experience as with [JetBrains IDEs](https://www.jetbrains.com/ides/).

We’re currently looking at various aspects, including the configuration of projects for building, packaging, publishing,
and more. At the current stage, however, the focus is primarily on configuring projects for building. While the current
use case is Kotlin and Kotlin Multiplatform, Amper also supports Java and Swift (as a requirement for multiplatform).
However, the same approach to configuration could work for other languages and technology stacks in the future.

Amper exists as a standalone build tool as well as a Gradle plugin for existing Gradle-based projects.
Both versions offer a unified, easy-to-use, declarative configuration format.

Supported features:
* Creating and running JVM, Android, iOS, Linux, and macOS applications.
* Creating Kotlin Multiplatform libraries.
* Running tests.
* Mixing Kotlin, Java and Swift code.
* Code assistance for [module files](docs/Documentation.md#module-file-anatomy) in IntelliJ IDEA and Fleet.
* [Multi-module](docs/Documentation.md#module-dependencies) projects.
* Using [Compose Multiplatform](docs/Documentation.md#configuring-compose-multiplatform).
* Using Compose [multiplatform resources](docs/Documentation.md#using-multiplatform-resources)*.
* [Gradle interop](docs/Documentation.md#gradle-interop), including combining Amper and Gradle modules in one project*.
* Integration with [Gradle version catalogs](docs/Documentation.md#dependencyversion-catalogs)*.
* [Gradle-compatible project layout](docs/Documentation.md#file-layout-with-gradle-interop) for the smooth migration of existing Gradle projects*.

*\* only in Gradle-based projects*

Future directions:
* Support for more Kotlin and Kotlin Multiplatform scenarios and configurations.
* More [product types](docs/Documentation.md#product-types) and platforms, such as watchOS, Windows, etc.
* [Platform-specific test types](docs/Documentation.md#special-types-of-tests), including android instrumented tests.
* [Native dependencies](docs/Documentation.md#native-dependencies) support, such as CocoaPods, Swift Package Manager.
* [Packaging](docs/Documentation.md#packaging) and [publication](docs/Documentation.md#publishing). 
* [Build variants](docs/Documentation.md#build-variants) support.
* [Extensibility](docs/Documentation.md#extensibility).

For a quick start:
* [Setup](docs/Setup.md) and [usage](docs/Usage.md) instructions
* [Tutorial](docs/Tutorial.md)  
* [Documentation](docs/Documentation.md) 
* [Example projects](examples-gradle)
* Gradle [migration guide](docs/GradleMigration.md)  

## Issues and feedback

Amper uses [YouTrack](https://youtrack.jetbrains.com/issues/AMPER) for issue tracking, [create a new issue](https://youtrack.jetbrains.com/newIssue?project=AMPER) there to report problems or submit ideas.

Before reporting an issue, please check the [FAQ](docs/FAQ.md).

You can also join the [Slack channel](https://kotlinlang.slack.com/archives/C062WG3A7T8) for discussions, or share your feedback using the [feedback form](https://surveys.jetbrains.com/s3/Amper-feedback-form).     

## How to Try
There are multiple ways to try Amper:

* In the latest [IntelliJ IDEA](https://www.jetbrains.com/idea/nextversion/), for JVM and Android projects ([instructions](docs/Usage.md#using-amper-in-intellij-idea)).
* In the latest [JetBrains Fleet](https://www.jetbrains.com/fleet/), for the JVM, Android, and Kotlin Multiplatform projects ([instructions](docs/Usage.md#using-amper-in-fleet)).
* Using [standalone Amper from command line](docs/Usage.md#using-the-standalone-amper-version-from-the-command-line).
* Using [Gradle-Based Amper from command line](docs/Usage.md#using-the-gradle-based-amper-version-from-the-command-line) to build Gradle-based Amper projects.

## Examples

### Basics
Here is a very basic JVM "Hello, World!" project:

<img src="docs/images/ij-jvm-structure.png" width="50%" alt="">


The `main.kt` and `MyTest.kt` files are just regular Kotlin files with nothing special in them. The interesting part is `module.yaml`, which is the Amper module configuration file. For the above project structure, it would simply be: 

```yaml
# Produce a JVM application 
product: jvm/app
```

That's it. The Kotlin and Java toolchains, test framework, and other necessary functionality are configured and available straight out of the box. You can build it, run it, write and run tests, and more. For more detailed information, check out the [full example](examples-gradle/jvm).

![](docs/images/ij-jvm-result.png)

### Multiplatform

Now, let's look at a Compose Multiplatform project with Android, iOS, and desktop JVM apps, with the following project structure in Fleet:

<img src="docs/images/fleet-kmp-structure.png" width="50%" alt="">

Notice how the `src/` folder contains Kotlin and Swift code together. It could, of course, also be Kotlin and Java.
Another aspect to highlight is the shared module with the common code in the `src` folder and the platform-specific code folders `src@ios` and `src@android` (learn more about [project layout](docs/Documentation.md#project-layout)).

Here is how `ios-app/module.yaml` file looks:
```yaml
# Produce an iOS application
product: ios/app

# Depend on the shared library module: 
dependencies:
  - ../shared

settings:
  # Enable the Compose Multiplatform framework
  compose: enabled
```

This is pretty straightforward: It defines an iOS application with a dependency on a shared module and enables the Compose Multiplatform framework. A more interesting example would be `shared/module.yaml`:

```yaml
# Produce a shared library for the JVM, Android, and iOS platforms:
product:
  type: lib
  platforms: [jvm, android, iosArm64, iosSimulatorArm64, iosX64]

# Shared Compose dependencies:
dependencies:
  - $compose.foundation: exported
  - $compose.material3: exported

# Android-only dependencies  
dependencies@android:
  # Android-specific integration with Compose
  - androidx.activity:activity-compose:1.7.2: exported
  - androidx.appcompat:appcompat:1.6.1: exported

# iOS-only dependencies with a dependency on a CocoaPod (not yet implemented)
dependencies@ios:
  - pod: 'Alamofire'
    version: '~> 2.0.1'

settings:
  # Enable Kotlin serialization
  kotlin:
    serialization: json
  
  # Enable the Compose Multiplatform framework
  compose: enabled
```

A couple of things are worth mentioning. First, note the platform-specific dependencies: sections with the `@<platform>` qualifier. [The platform qualifier](docs/Documentation.md#platform-qualifier) can be used both in the manifest and also in the file layout. The qualifier organizes the code, dependencies, and settings for a certain platform.
Second, the dependencies: section allows not only Kotlin and Maven dependencies, but also [platform-specific package managers](docs/Documentation.md#native-dependencies), such as CocoaPods, Swift Package Manager, and others.

![](docs/images/fleet-kmp-result.png)

Naturally, these examples show only a limited set of Amper features. 
To get more insight into design and functionality, look at the [documentation](docs/Documentation.md), the [tutorial](docs/Tutorial.md), 
and [example projects](#more-examples).     

### More examples
Check our these example projects:
* JVM "Hello, World!" ([standalone](examples-standalone/jvm), [Gradle-based](examples-gradle/jvm))
* Compose Multiplatform ([standalone](examples-standalone/compose-multiplatform), [Gradle-based](examples-gradle/compose-multiplatform))
* Compose on iOS ([standalone](examples-standalone/compose-ios), [Gradle-based](examples-gradle/compose-ios)),
  Android ([standalone](examples-standalone/compose-android), [Gradle-based](examples-gradle/compose-android)),
  and desktop ([standalone](examples-standalone/compose-desktop), [Gradle-based](examples-gradle/compose-desktop))
* Kotlin Multiplatform app template with [shared Compose UI](https://github.com/Kotlin/KMP-App-Template/tree/amper) 
* and with [native Android and iOS UI](https://github.com/Kotlin/KMP-App-Template-Native/tree/amper)  
* Gradle interop in the [Gradle-based Amper](examples-gradle/gradle-interop)
* And other [standalone](examples-standalone) and [Gradle-based](examples-gradle) examples
