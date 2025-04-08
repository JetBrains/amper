## Before you start

Check the [setup instructions](Setup.md)

## Basics

Amper is a project configuration and build tool.
See the [usage instructions](Usage.md#using-amper-from-the-command-line) to get started with the standalone CLI. 

Also, for existing Gradle projects there is [Amper Gradle plugin](#gradle-based-projects) which offers full Gradle interop. Note that certain functionality and behavior might differ between the standalone and Gradle-based Amper versions.

An Amper **project** is defined by a `project.yaml` file. This file contains the list of modules and the project-wide
configuration. The folder with the `project.yaml` file is the project root. Modules can only be located under the
project root. If there is only one module in the project, a `project.yaml` file is not required.

> In Gradle-based projects, a `settings.gradle.kts` file is used instead of a `project.yaml` file.

An Amper **module** is a directory with a `module.yaml` configuration file, module sources and resources.
A *module configuration file* describes _what_ to produce: e.g. a reusable library or a platform-specific application.
Each module describes a single product. Several modules can't share same sources or resources.

> To get familiar with YAML, see [the brief intro](#brief-yaml-reference).

_How_ to produce the desired product, that is, the build rules, is the responsibility of the Amper build engine
and [extensions](#extensibility).
In Gradle-based Amper projects it's possible to use [plugins](Documentation.md#using-gradle-plugins) and
write [custom Gradle tasks](#writing-custom-gradle-tasks).

Amper supports Kotlin Multiplatform as a core concept and offers special syntax to deal with multiplatform
configuration. There is a dedicated [**@platform-qualifier**](#platform-qualifier) used to mark platform-specific
dependencies, settings, etc. You'll see it in the examples below.

## Project layout

A basic single-module Amper project looks like this:

```
|-src/             
|  |-main.kt      
|-test/       
|  |-MainTest.kt 
|-module.yaml
```

If there are multiple modules, the `project.yaml` file specifies the list of modules:

```
|-app/
|  |-src/             
|  |  |-main.kt
|  |-...      
|  |-module.yaml
|-lib/
|  |-src/             
|  |  |-util.kt      
|  |-module.yaml
|-project.yaml
```

In the above case, the `project.yaml` looks like this:

```yaml
modules:
  - ./app
  - ./lib
```

Check the [reference](DSLReference.md#modules) for more options to define the list of modules in the `project.yaml` file.

In Gradle-based projects, a `settings.gradle.kts` file is expected instead of a `project.yaml` file, and it's required 
even for single-module projects.
Read more in the [Gradle-based projects](Documentation.md#gradle-based-projects) section.
```
|-src/             
|  |-main.kt      
|-test/       
|  |-MainTest.kt 
|-module.yaml
|-settings.gradle.kts
```

### Source code

Source files are located in the `src` folder:

```
|-src/             
|  |-main.kt      
|-module.yaml
```

By convention, a `main.kt` file, if present, is the default entry point for the application.
Read more on [configuring the application entry points](#configuring-entry-points).

In a JVM module, you can mix Kotlin and Java source files:
```
|-src/             
|  |-main.kt      
|  |-Util.java      
|-module.yaml
```

In a [multiplatform module](#multiplatform-projects), platform-specific code is located in folders
with [`@platform`-qualifiers](#platform-qualifier):
```
|-src/             # common code
|  |-main.kt      
|  |-util.kt       #  API with ‘expect’ part
|-src@ios/         # code to be compiled only for iOS targets
|  |-util.kt       #  API implementation with ‘actual’ part for iOS
|-src@jvm/         # code to be compiled only for JVM targets
|  |-util.kt       #  API implementation with ‘actual’ part for JVM
|-module.yaml
```

Sources and resources can't be shared by multiple modules. This ensures that the IDE always knows how to analyze and
refactor the code, as it always exists in the scope of a single module, has a well-defined list of dependencies, etc.

See also info on [resource management](#resources).

Amper also supports Gradle-compatible layouts for [Gradle-based](#gradle-based-projects) projects:

```
|-src/
|  |-main/
|  |  |-kotlin
|  |  |  |-main.kt
|  |  |-resources
|  |  |  |-...
|  |-test/
|  |  |-kotlin
|  |  |  |-test.kt
|  |  |-resources
|  |  |  |-...
|-module.yaml
```
Read more about [Gradle-compatible project layouts](#file-layout-with-gradle-interop).

## Module file anatomy

A `module.yaml` file has several main sections: `product:`, `dependencies:` and `settings:`. A module can produce a
single product, such as a reusable library or an application.
Read more on the [supported product types](#product-types).

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

- `lib` - a reusable Amper library which can be used as a dependency by other modules in the Amper project.
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
See [details on multiplatform configuration](#multiplatform-configuration) for more information.

### Dependencies

#### External Maven dependencies

For Maven dependencies, simply specify their coordinates:
```yaml
dependencies:
  - org.jetbrains.kotlin:kotlin-serialization:1.8.0
  - io.ktor:ktor-client-core:2.2.0
```

#### Module dependencies

To depend on another module, use a relative path to the folder which contains the corresponding `module.yaml`.
Module dependency should start either with `./` or `../`.

> Dependencies between modules are only allowed within the project scope.
> That is, they must be listed in the `project.yaml` or `settings.gradle.kts` file.

Example: given the project layout

```
root/
  |-app/
  |  |-src/
  |  |-module.yaml
  |-ui/
  |  |-utils/
  |  |  |-src/
  |  |  |-module.yaml
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

#### Scopes and visibility

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
In order to make a dependency visible to a dependent module, you need to explicitly mark it as `exported` (aka Gradle API-dependency). 

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

### Managing Maven repositories

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

> Currently only `*.properties` files with credentials are supported.

**Note on Gradle interop**

If some repositories are defined in `settings.gradle.kts` using a `dependencyResolutionManagement` block, they are only
taken into account by pure Gradle subprojects, and don't affect Amper modules. If you want to define repositories in a
central place for Amper modules, you can use a `repositories` list in a [template file](#templates) and apply this
template to your modules.

Technical explanation: in Gradle, adding any repository at the subproject level will by default discard the repositories
configured in the settings (unless a different Gradle
[RepositoriesMode](https://docs.gradle.org/current/javadoc/org/gradle/api/initialization/resolve/RepositoriesMode.html)
is used). Default repositories provided by Amper is an equivalent to adding a `repositories` section in
the `build.gradle.kts` file of each individual Amper module.

### Library Catalogs (a.k.a Version Catalogs)

A library catalog associates keys to library coordinates (including the version), and allows adding the same libraries
as dependencies to multiple modules without having to repeat the coordinates or the versions of the libraries.

Amper currently supports 2 types of dependency catalogs:
- toolchain catalogs (such as Kotlin, Compose Multiplatform etc.)
- Gradle version catalogs that are placed in the default [gradle/libs.versions.toml file](https://docs.gradle.org/current/userguide/version_catalogs.html#sec:version-catalog-declaration)

The toolchain catalogs are implicitly defined, and contain predefined libraries that relate to the corresponding toolchain.
The name of such a catalog corresponds to the [name of the corresponding toolchain in the settings section](#settings).
For example, dependencies for the Compose Multiplatform frameworks are accessible using the `$compose` catalog.

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

Module dependencies can still have a [scope and visibility](#scopes-and-visibility) even when coming from a catalog:

```yaml
dependencies:
  - $compose.foundation: exported
  - $my-catalog.db-engine: runtime-only 
```

### Settings

The `settings:` section contains toolchains settings.
A _toolchain_ is an SDK (Kotlin, Java, Android, iOS) or a simpler tool (linter, code generator).

> Toolchains are supposed to be [extensible](#extensibility) in the future.

All toolchain settings are specified in the dedicated groups in the `settings:` section:
```yaml
settings:
  kotlin:
    languageVersion: 1.8
  android:
    compileSdk: 31
```

Here is the list of [currently supported toolchains and their settings](DSLReference.md#settings-and-test-settings).   

See [multiplatform settings configuration](#multiplatform-settings) for more details.

#### Configuring Kotlin Serialization

[Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization) is the official multiplatform and multi-format
serialization library for Kotlin.

If you need to (de)serialize Kotlin classes to/from JSON, you can enable Kotlin Serialization it in its simplest form:
```yaml
settings:
  kotlin:
    serialization: json  # JSON or other format
```
This snippet configures the compiler to process `@Serializable` classes, and adds dependencies on the serialization
runtime and JSON format libraries.

You can also customize the version of the Kotlin Serialization libraries using the full form of the configuration:

```yaml
settings:
  kotlin:
    serialization:
      format: json
      version: 1.7.3
```

##### More control over serialization formats

If you don't need serialization format dependencies or if you need more control over them, you can use the following:
```yaml
settings:
  kotlin:
    serialization: enabled # configures the compiler and serialization runtime library
```
This snippet on its own only configures the compiler and the serialization runtime library, but doesn't add any format
dependency. However, it adds a built-in catalog with official serialization formats libraries, which you can use in 
your `dependencies` section. This is useful in multiple cases:

* if you need a format dependency only in tests:
  ```yaml
  settings:
    kotlin:
      serialization: enabled
  
  test-dependencies:
    - $kotlin.serialization.json
  ```

* if you need to customize the scope of the format dependencies:
  ```yaml
  settings:
    kotlin:
      serialization: enabled
  
  dependencies:
    - $kotlin.serialization.json: compile-only
  ```

* if you need to expose format dependencies transitively:
  ```yaml
  settings:
    kotlin:
      serialization: enabled
  
  dependencies:
    - $kotlin.serialization.json: exported
  ```

* if you need multiple formats:
  ```yaml
  settings:
    kotlin:
      serialization: enabled
  
  dependencies:
    - $kotlin.serialization.json
    - $kotlin.serialization.protobuf
  ```

#### Configuring Compose Multiplatform

In order to enable [Compose](https://www.jetbrains.com/lp/compose-multiplatform/) (with a compiler plugin and required dependencies), add the following configuration:

JVM Desktop:
```yaml
product: jvm/app

dependencies:
  # add Compose dependencies using a dependency catalog:
  - $compose.desktop.currentOs
    
settings: 
  # enable Compose toolchain
  compose: enabled
```

Android:
```yaml
product: android/app

dependencies:
  # add Compose dependencies using a dependency catalog:
  - $compose.foundation
  - $compose.material3

settings: 
  # enable Compose toolchain
  compose: enabled
```

There is also a full form for enabling or disabling the Compose toolchain:
```yaml
...
settings: 
  compose:
    enabled: true
```

Also, you can specify the exact version of the Compose framework to use:

```yaml
...
settings:
  compose:
    version: 1.5.10
```

> In a [Gradle-based project](#gradle-based-projects) you also need to set a couple of flags in the `gradle.properties`
> file:

```
|-...
|-settings.gradle.kts
|-gradle.properties    # create this file if it doesn't exist 
``` 

```properties
# Compose requires AndroidX
android.useAndroidX=true

# Android and iOS build require more memory, so we set -Xmx4g.
# The other options are just Gradle defaults that we restore because they are overridden as soon as we use this property
org.gradle.jvmargs=-Xmx4g -Xms256m -XX:MaxMetaspaceSize=384m -XX:+HeapDumpOnOutOfMemoryError
```

##### Using multiplatform resources

Amper supports [Compose Multiplatform resources](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-images-resources.html).

The file layout for the standalone Amper is:
```
|-my-kmp-module/
|  |-module.yaml
|  |-src/ # your code is here
|  |  |-...
|  |-composeResources/ # place your multiplatform resources in this folder
|  |  |-values/
|  |  |  |-strings.xml
|  |  |-drawable/
|  |  |  |-image.jpg
|  |-...
```

To use multiplatform resources in a Gradle-based project,
a module must be configured with a [Gradle-compatible file layout](#file-layout-with-gradle-interop):
```
|-my-kmp-module/
|  |-module.yaml
|  |-src/
|  |  |-commonMain/
|  |  |  |-kotlin # your code is here
|  |  |  |  |-...
|  |  |  |-composeResources # place your multiplatform resources in this folder
|  |  |  |  |-values/
|  |  |  |  |  |-strings.xml
|  |  |  |  |-drawable/
|  |  |  |  |  |-image.jpg
|  |  |-...
```

Configure the `module.yaml` to use `gradle-kmp` file layout:
```yaml
product: 
  type: lib
  platforms: [jvm, android]

module:
  layout: gradle-kmp 
```

Amper automatically generates the accessors for resources during the build and when working with code in the IDE.
Accessors are generated in a package that corresponds to the module name. All non-letter symbols are replaced with `_`.
In the given example where the module name is `my-kmp-module`, the package name for the generated resources 
will be `my_kmp_module`.

Here is how to use the resources in the code:

```kotlin
import my_kmp_module.generated.resources.Res
import my_kmp_module.generated.resources.hello
// other imports

@Composable
private fun displayHelloText() {
    BasicText(stringResource(Res.string.hello))
}
```

Read more about setting up and using compose resources in [the documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-images-resources.html).

#### Configuring entry points

##### JVM
By convention, a single `main.kt` file (case-insensitive) in the source folder is the default entry point for the application.

Here is how to specify the entry point explicitly for JVM applications (main class):
```yaml
product: jvm/app

settings:
  jvm:
    mainClass: org.example.myapp.MyMainKt
```
##### Native
By convention, a single `main.kt` file (case-insensitive) in the source folder is the default entry point for the application.

##### Android

See the [dedicated Android section](#entry-point)

##### iOS

Currently, there should be a swift file in the `src/` folder with the `@main` struct:
```
|-src/ 
|  |-main.swift
|  |-... 
|-module.yaml
```

src/main.swift:
```swift
...
@main
struct iosApp: App {
   ...
}
```

#### Configuring Android

Use the `android/app` product type in a module to build an Android application.

##### Entry point

The application's entry point is specified in the AndroidManifest.xml file according to the 
[official Android documentation](https://developer.android.com/guide/topics/manifest/manifest-intro).
```
|-src/ 
|  |-MyActivity.kt
|  |-AndroidManifest.xml
|  |-... 
|-module.yaml
```

`src/AndroidManifest.xml`:
```xml
<manifest ... >
  <application ... >
    <activity android:name="com.example.myapp.MainActivity" ... >
    </activity>
  </application>
</manifest>
```

You can run your application using the `run` command.

##### Packaging

You can use the `build` command to create an APK, or the `package` command to create an Android Application Bundle (AAB).

The `package` command will not only build the APK, but also minify/obfuscate it with ProGuard, and sign it.
See the dedicated [signing](#signing) and [code shrinking](#code-shrinking) sections to learn how to configure this. 

> In Gradle-based Amper projects, you can use the Gradle tasks provided by the Android Gradle plugin.

#### Code shrinking

When creating a release build with Amper, R8 will be used automatically, with minification and shrinking enabled.
This is equivalent to the following Gradle configuration:

```kotlin
// in Gradle
isMinifyEnabled = true
isShrinkResources = true
proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
```

You can create a `proguard-rules.pro` file in the module folder to add custom rules for R8.

```
|-src/
|  ...      
|-test/
|  ...
|-proguard-rules.pro
|-module.yaml
```

It is automatically used by Amper if present.

An example of how to add custom R8 rules can be found [in the android-app module](../examples-standalone/compose-multiplatform/android-app/proguard-rules.pro) of the `compose-multiplatform` example project.

##### Signing

In a module containing an Android application (using the `android/app` product type) you can enable signing under
settings:

```yaml
settings:
  android:
    signing: enabled
```

This will use a `keystore.properties` file located in the module folder for the signing details by default. This
properties file must contain the following signing details. **Remember that these details should usually not be added
to version control.**

```properties
storeFile=/Users/example/.keystores/release.keystore
storePassword=store_password
keyAlias=alias
keyPassword=key_password
```

To customize the path to this file, you can use the `propertiesFile` option:

```yaml
settings:
  android:
    signing:
      enabled: true
      propertiesFile: ./keystore.properties # default value
```

With the standalone version of Amper, you can use `./amper tool generate-keystore` to generate a new keystore if you
don't have one yet. This will create a new self-signed certificate, using the details in the `keystore.properties` file.

> You can also pass in these details to `generate-keystore` as command line arguments. Invoke the tool with `--help`
> to learn more.

##### Parcelize

If you want to automatically generate your `Parcelable` implementations, you can enable 
[Parcelize](https://developer.android.com/kotlin/parcelize) as follows:

```yaml
settings:
  android:
    parcelize: enabled
```

With this simple toggle, the following class gets its `Parcelable` implementation automatically without spelling it out
in the code, just thanks to the `@Parcelize` annotation:
```kotlin
import kotlinx.parcelize.Parcelize

@Parcelize
class User(val firstName: String, val lastName: String, val age: Int): Parcelable
```

While this is only relevant on Android, sometimes you need to share your data model between multiple platforms.
However, the `Parcelable` interface and `@Parcelize` annotation are only present on Android.
But fear not, there is a solution described in the
[official documentation](https://developer.android.com/kotlin/parcelize#setup_parcelize_for_kotlin_multiplatform).
In short:

* For `android.os.Parcelable`, you can use the `expect`/`actual` mechanism to define your own interface as typealias of 
`android.os.Parcelable` (for Android), and as an empty interface for other platforms.
* For `@Parcelize`, you can simply define your own annotation instead, and then tell Parcelize about it (see below).

For example, in common code:
```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class MyParcelize

expect interface MyParcelable
```
Then in Android code:
```kotlin
actual typealias MyParcelable = android.os.Parcelable
```
And in other platforms:
```kotlin
// empty because nothing is generated on non-Android platforms
actual interface MyParcelable
```

You can then make Parcelize recognize this custom annotation using the `additionalAnnotations` option:

```yaml
settings:
  kotlin:
    # for the expect/actual MyParcelable interface
    freeCompilerArgs: [ -Xexpect-actual-classes ]
  android:
    parcelize:
      enabled: true
      additionalAnnotations: [ com.example.MyParcelize ]
```


#### Google Services and Firebase

To enable the [`google-services` plugin](https://developers.google.com/android/guides/google-services-plugin), place your `google-services.json` file in the module containing an `android/app` product, next to `module.yaml`.
    
```
|-androidApp/
|  |-src/
|  |-google-services.json
|  |-module.yaml
```
 
This file will be consumed automatically in both standalone and Gradle-based Amper projects.

#### Configuring Kotlin Symbol Processing (KSP)

[Kotlin Symbol Processing](https://kotlinlang.org/docs/ksp-overview.html) is a tool that allows feeding Kotlin source
code to _processors_, which can in turn use this information to generate code, classes, or resources, for instance.
Amper provides built-in support for KSP.

Some popular libraries also include a KSP processor to enhance their capabilities, such as
[Room](https://developer.android.com/jetpack/androidx/releases/room) or
[Moshi](https://github.com/square/moshi#codegen).

> Note: Amper works with KSP2, so any processors used must be compatible with KSP2.
> We’re expecting most processors to make this upgrade soon, as KSP1 is deprecated and will not support Kotlin 2.1.
> However, at the moment, you might still see some gaps in support, such as issues with native targets.

To add KSP processors to your module, add their maven coordinates to the `settings.kotlin.ksp.processors` list:

```yaml
settings:
  kotlin:
    ksp:
      processors:
        - androidx.room:room-compiler:2.7.0-alpha12
```

In multiplatform modules, all settings from the `settings` section apply to all platforms by default, including KSP processors.
If you only want to add KSP processors for a specific platform, use a `settings` block with a
[platform qualifier](#platform-qualifier):

```yaml
# the Room processor will only process code that compiles to the Android platform
settings@android:
  kotlin:
    ksp:
      processors:
        - androidx.room:room-compiler:2.7.0-alpha12
```

Some processors can be customized by passing options. You can pass these options using the `processorOptions` section:

```yaml
settings:
  kotlin:
    ksp:
      processors:
        - androidx.room:room-compiler:2.7.0-alpha12
      processorOptions:
        room.schemaLocation: ./schema
```

> Note: all options are passed to all processors by KSP. It's the processor's responsibility to use unique option names
> to avoid clashes with other processor options.

##### Using your own local KSP processor

You can implement your own processor in an Amper module as a regular JVM library, and then use it to process code from
other modules in your project.

Usually, 3 modules are involved:
* The _processor_ module, with the actual processor implementation
* The _annotations_ module (optional), which contains annotations that the processor looks for in the consumer code
* The _consumer_ module, which uses KSP with the custom processor

The annotations module is a very simple JVM library module without any required dependencies (it's just here to provide
some annotations to work with, if necessary):
```yaml
# my-processor-annotations/module.yaml
product:
  type: lib
  platforms: [ jvm ]
```

The processor module is a JVM library with a `compile-only` dependency on KSP facilities, and on the custom annotations module:
```yaml
# my-processor/module.yaml
product:
  type: lib
  platforms: [ jvm ]

dependencies:
  - ../my-processor-annotations
  - com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.25: compile-only
```

The consumer module adds a regular dependency on the annotations module, and a reference to the processor module: 
```yaml
# my-consumer/module.yaml
product: jvm/app

dependencies:
  - ../my-processor-annotations # to be able to annotate the consumer code

settings:
  kotlin:
    ksp:
      processors:
        - ../my-processor # path to the module implementing the KSP processor
```

For more information about how to write your own processor, check out
[the KSP documentation](https://kotlinlang.org/docs/ksp-quickstart.html#create-a-processor-of-your-own).

### Tests

Test code is located in the `test/` folder:
```
|-src/            # production code
|  ...      
|-test/           # test code 
|  |-MainTest.kt
|  |-... 
|-module.yaml
```

By default, the [Kotlin test](https://kotlinlang.org/api/latest/kotlin.test/) framework is preconfigured for each
platform. Additional test-only dependencies should be added to the `test-dependencies:` section of your module
configuration file:

```yaml
product: jvm/app

# these dependencies are available in main and test code
dependencies:
  - io.ktor:ktor-client-core:2.2.0

# additional dependencies for test code
test-dependencies:
  - io.ktor:ktor-server-test-host:2.2.0
```

To add or override [toolchain settings](#settings) in tests, use `test-settings:` section:
```yaml
# these dependencies are available in main and test code
setting:
  kotlin:
    ...

# additional test-specific setting 
test-settings:
  kotlin:
    ...
```

Test settings and dependencies by default are inherited from the main configuration according to the [configuration propagation rules](#dependencysettings-propagation). 
Example:
```
|-src/             
|  ...      
|-src@ios/             
|  ...      
|-test/           # Sees declarations from src/. Executed on all platforms. 
|  |-MainTest.kt
|  |-... 
|-test@ios/       # Sees declarations from test/ and src@ios/. Executed on iOS platforms only.  
|  |-IOSTest.kt 
|  |-... 
|-module.yaml
```

```yaml
product:
  type: lib
  platforms: [android, iosArm64]

# these dependencies are available in main and test code
dependencies:
  - io.ktor:ktor-client-core:2.2.0

# dependencies for test code
test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.10
  
# these settings affect the main and test code
settings: 
  kotlin:
    languageVersion: 1.8

# these settings affect tests only
test-settings:
  kotlin:
    languageVersion: 1.9 # overrides `settings:kotlin:languageVersion: 1.8`
```

## Resources

Files placed into the `resources` folder are copied to the resulting products:
```
|-src/             
|  |-...
|-resources/     # These files are copied into the final products
|  |-...
```

In [multiplatform modules](#multiplatform-configuration) resources are merged from the common folders and corresponding platform-specific folders:
```
|-src/             
|  |-...
|-resources/          # these resources are copied into the Android and JVM artifact
|  |-...
|-resources@android/  # these resources are copied into the Android artifact
|  |-...
|-resources@jvm/      # these resources are copied into the JVM artifact
|  |-...
```

In case of duplicating names, the common resources are overwritten by the more specific ones. 
That is `resources/foo.txt` will be overwritten by `resources@android/foo.txt`.  

Android modules also have [res and assets](https://developer.android.com/guide/topics/resources/providing-resources) folders:
```
|-src/             
|  |-...
|-res/
|  |-drawable/
|  |  |-...
|  |-layout/
|  |  |-...
|  |-...
|-assets/
|  |-...
|-module.yaml
```

## Interop between languages

Kotlin Multiplatform implies smooth interop with platform languages, APIs, and frameworks.
There are three distinct scenarios where such interoperability is needed:

- Consuming: Kotlin code can use APIs from existing platform libraries, e.g. jars on JVM or CocoaPods on iOS.  
- Publishing: Kotlin code can be compiled and published as platform libraries to be consumed by the target platform's tooling; such as jars on JVM, *.so on linux or frameworks on iOS.    
- Joint compilation: Kotlin code be compiled and linked into a final product together with the platform languages, like JVM, C, Objective-C and Swift.

> Kotlin JVM supported all these scenarios from the beginning.
> However, full interoperability is currently not supported in Kotlin Native.

Here is how the interop is designed to work in the current Amper design:

Consuming: Platform libraries and package managers could be consumed using a dedicated (and [extensible](#extensibility)) [dependency notation](#native-dependencies):

```yaml
dependencies:
  # Kotlin or JVM dependency
  - io.ktor:ktor-client-core:2.2.0

  # JS npm dependency
  - npm: "react"
    version: "^17.0.2"

  # iOS CocoaPods dependency
  - pod: 'Alamofire'
    version: '~> 2.0.1'
```

Publishing: In order to create a platform library or a package different [packaging types](#packaging) are supported (also [extensible](#extensibility)):

```yaml
publishing:
  # publish as JVM library 
  - maven: lib        
    groupId: ...
    artifactId: ...

  # publish as iOS CocoaPods framework 
  - cocoapods: ios/framework
    name: MyCocoaPod
    version: 1.0
    summary: ...
    homepage: ...
```

Joint compilation is already supported for Java and Kotlin, and in the future Kotlin Native will also support joint Kotlin+Swift compilation.

From the user's point of view, the joint compilation is transparent; they could simply place the code written in different languages into the same source folder:

```
|-src/             
|  |-main.kt      
|-src@jvm/             
|  |-KotlinCode.kt      
|  |-JavaCode.java      
|-src@ios/             
|  |-KotlinCode.kt 
|  |-SwiftCore.swift
|-module.yaml
```

## Multiplatform projects

### Platform qualifier

Use the `@platform`-qualifier to mark platform-specific source folders and sections in the `module.yaml` files. 
You can use Kotlin Multiplatform [platform names](https://kotlinlang.org/docs/native-target-support.html) and families as `@platform`-qualifier.
```yaml
dependencies:               # common dependencies for all platforms
dependencies@ios:           # ios is a platform family name  
dependencies@iosArm64:      # iosArm64 is a KMP platform name
```
```yaml
settings:                   # common settings for all platforms
settings@ios:               # ios is a platform family name  
settings@iosArm64:          # iosArm64 is a KMP platform name
```
```
|-src/                      # common code for all platforms
|-src@ios/                  # sees declarations from src/ 
|-src@iosArm64/             # sees declarations from src/ and from src@ios/ 
```

See also how the [resources](#resources) are handled in the multiplatform projects.  


Only the platform names (but not the platform family names) can be currently used in the `platforms:` list:

```yaml
product:
  type: lib
  platforms: [iosArm64, android, jvm]
```

### Platforms hierarchy

Some target platforms belong to the same family and share some common APIs.
They form a hierarchy as follows:
```yaml
common  # corresponds to src directories or configuration sections without @platform suffix
  jvm
  android  
  native
    linux
      linuxX64
      linuxArm64
    mingw
      mingwX64
    apple
      macos
        macosX64
        macosArm64
      ios
        iosArm64
        iosSimulatorArm64
        iosX64            # iOS Simulator for Intel Mac
      watchos
        watchosArm32
        watchosArm64
        watchosDeviceArm64
        watchosSimulatorArm64
        watchosX64
      tvos
        tvosArm64
        tvosSimulatorArm64
        tvosX64
  ...
```

> Note: not all platforms listed here are equally supported or tested.
> Additional platforms may also exist in addition to the ones listed here, but are also untested/highly experimental. 

Based on this hierarchy, common code is visible from more `@platform`-specific code, but not vice versa:
```
|-src/             
|  |-...      
|-src@ios/                  # sees declarations from src/ 
|  |-...      
|-src@iosArm64/             # sees declarations from src/ and from src@ios/ 
|  |-...      
|-src@iosSimulatorArm64/    # sees declarations from src/ and from src@ios/ 
|  |-...      
|-src@jvm/                  # sees declarations from src/
|  |-...      
|-module.yaml
```

You can therefore share code between platforms by placing it in a common ancestor in the hierarchy:
code placed in `src@ios` is shared between `iosArm64` and `iosSimulatorArm64`, for instance.

For [Kotlin Multiplatform expect/actual declarations](https://kotlinlang.org/docs/multiplatform-connect-to-apis.html), put your `expected` declarations into the `src/` folder, and `actual` declarations into the corresponding `src@<platform>/` folders. 

This hierarchy applies to `@platform`-qualified sections in the configuration files as well.
We'll see how this works more precisely in the [Multiplatform Dependencies](#multiplatform-dependencies) and
[Multiplatform Settings](#multiplatform-settings) sections.

#### Aliases

If the default hierarchy is not enough, you can create custom `aliases`, each corresponding to a group of target platforms.
You can then use the alias in places where `@platform` suffixes usually appear to share code or configuration:

```yaml
product:
  type: lib
  platforms: [iosArm64, android, jvm]

aliases:
  - jvmAndAndroid: [jvm, android] # defines a custom alias for this group of platforms

# these dependencies will be visible in jvm and android code
dependencies@jvmAndAndroid:
  - org.lighthousegames:logging:1.3.0

# these dependencies will be visible in jvm code only
dependencies@jvm:
  - org.lighthousegames:logging:1.3.0
```

```
|-src/             
|-src@jvmAndAndroid/ # sees declarations from src/ 
|-src@jvm/           # sees declarations from src/ and src@jvmAndAndroid/              
|-src@android/       # sees declarations from src/ and src@jvmAndAndroid/             
```

### Multiplatform dependencies

When you use a Kotlin Multiplatform library, its platforms-specific parts are automatically configured for each module platform.

Example:
To add a [KmLogging library](https://github.com/LighthouseGames/KmLogging) to a multiplatform module, simply write

```yaml
product:
  type: lib
  platforms: [android, iosArm64, jvm]

dependencies:
  - org.lighthousegames:logging:1.3.0
```
The effective dependency lists are:
```yaml
dependencies@android:
  - org.lighthousegames:logging:1.3.0
  - org.lighthousegames:logging-android:1.3.0
```
```yaml
dependencies@iosArm64:
  - org.lighthousegames:logging:1.3.0
  - org.lighthousegames:logging-iosarm64:1.3.0
```
```yaml
dependencies@jvm:
  - org.lighthousegames:logging:1.3.0
  - org.lighthousegames:logging-jvm:1.3.0
```

For the explicitly specified dependencies in the `@platform`-sections the general [propagation rules](#dependencysettings-propagation) apply. That is, for the given configuration:
```yaml
product:
  type: lib
  platforms: [android, iosArm64, iosSimulatorArm64]
  
dependencies:
  - ../foo
dependencies@ios:
  - ../bar
dependencies@iosArm64:
  - ../baz
```
The effective dependency lists are:
```yaml
dependencies@android:
  ../foo
```
```yaml
dependencies@iosSimulatorArm64:
  ../foo
  ../bar
```
```yaml
dependencies@iosArm64:
  ../foo
  ../bar
  ../baz
```
### Multiplatform settings

All toolchain settings, even platform-specific can be placed in the `settings:` section:
```yaml
product:
  type: lib
  platforms: [android, iosArm64]

settings:
  # Kotlin toolchain settings that are used for both platforms
  kotlin:
    languageVersion: 1.8

  # Android-specific settings are used only when building for android
  android:
    compileSdk: 33

  # iOS-specific settings are used only when building for iosArm64
  ios:
    deploymentTarget: 17
```

There are situations when you need to override certain settings for a specific platform only. You can use `@platform`-qualifier. 

Note that certain platform names match the toolchain names, e.g. iOS and Android:
- `settings@ios` qualifier specifies settings for all iOS target platforms
- `settings:ios:` is an iOS toolchain settings   

This could lead to confusion in cases like:
```yaml
product: ios/app

settings@ios:    # settings to be used for iOS target platform
  ios:           # iOS toolchain settings
    deploymentTarget: 17
  kotlin:        # Kotlin toolchain settings
    languageVersion: 1.8
```
Luckily, there should rarely be a need for such a configuration.
We also plan to address this by linting with conversion to a more readable form:   
```yaml
product: ios/app

settings:
  ios:           # iOS toolchain settings
    deploymentTarget: 17
  kotlin:        # Kotlin toolchain settings
    languageVersion: 1.8
```

For settings with the `@platform`-qualifiers, the [propagation rules](#dependencysettings-propagation) apply.
E.g., for the given configuration:
```yaml
product:
  type: lib
  platforms: [android, iosArm64, iosSimulatorArm64]

settings:           # common toolchain settings
  kotlin:           # Kotlin toolchain
    languageVersion: 1.8
    freeCompilerArgs: [x]
  ios:              # iOS toolchain
    deploymentTarget: 17

settings@android:   # specialization for Android platform
  compose: enabled  # Compose toolchain

settings@ios:       # specialization for all iOS platforms
  kotlin:           # Kotlin toolchain
    languageVersion: 1.9
    freeCompilerArgs: [y]

settings@iosArm64:  # specialization for iOS arm64 platform 
  ios:              # iOS toolchain
    deploymentTarget: 18
```
The effective settings are:
```yaml 
settings@android:
  kotlin:
    languageVersion: 1.8   # from settings:
    freeCompilerArgs: [x]  # from settings:
  compose: enabled         # from settings@android:
```
```yaml 
settings@iosArm64:
  kotlin:
    languageVersion: 1.9      # from settings@ios:
    freeCompilerArgs: [x, y]  # merged from settings: and settings@ios:
  ios:
    deploymentTarget: 18      # from settings@iosArm64:
```
```yaml 
settings@iosSimulatorArm64:
  kotlin:
    languageVersion: 1.9      # from settings@ios:
    freeCompilerArgs: [x, y]  # merged from settings: and settings@ios:
  ios:
    deploymentTarget: 17      # from settings:
```

### Dependency/Settings propagation

Common `dependencies:` and `settings:` are automatically propagated to the platform families and platforms in `@platform`-sections, using the following rules:
- Scalar values (strings, numbers etc.) are overridden by more specialized `@platform`-sections.
- Mappings and lists are appended.

Think of the rules like adding merging Java/Kotlin Maps.

## Compose Hot Reload (experimental)

Amper supports [Compose Hot Reload](https://github.com/JetBrains/compose-hot-reload), allowing you to see UI changes in
real-time without restarting the application. This significantly improves the developer experience by shortening the
feedback loop during UI development.

### Configuration

To enable Compose Hot Reload, use a `jvm/app` module, and set `compose.experimental.hotReload` to `enabled`:

```yaml
product: jvm/app

settings:
  compose:
    enabled: true
    experimental:
      hotReload: enabled
```

When you run your application with Compose Hot Reload enabled:

- Amper automatically downloads and runs [JetBrains Runtime](https://github.com/JetBrains/JetBrainsRuntime) to maximize
  hot-swap capabilities
- A Java agent for Compose Hot Reload is attached during execution
- A small Compose Hot Reload devtools icon appears next to the application window, indicating that the feature is active

![Compose Hot Reload dev tools](images/hot-reload-devtools.png)

### IDE Integration

When running your app from the IDE, you can get automatic recompilation and reloading based on file system changes,
using the [Amper IntelliJ plugin](https://plugins.jetbrains.com/plugin/23076-amper).

To configure this:

1. Open *Settings → Tools → Actions on Save* and enable *Reload composition*
   ![Compose Hot Reload action on save](images/hot-reload-on-save-action.png)
2. Enable Compose Hot Reload in your `module.yaml` as shown above
3. Sync your project again
4. Add the `@DevelopmentEntryPoint` annotation to the composable you want to run
5. Click the gutter icon to run a development application with the selected composable
   ![Compose Hot Reload gutter icon](images/hot-reload-guttericon.png)

### Running from the command line

To use Compose Hot Reload while running the app from the command line, wrap your main composable function with the
`DevelopmentEntryPoint` composable:

```kotlin
fun main() = singleWindowApplication(title = "Compose for Desktop") {
    DevelopmentEntryPoint {
        App()
    }
}
```

You can then run your application normally:

```bash
./amper run
```

> Note: As Amper doesn't support observing file system changes to rebuild, you need to manually press the "reload"
> button on the dev tool to recompile and reload code changes when running from the command line.

### Limitations

- Compose Hot Reload support in Amper may change in future releases
- Only the `jvm` target is supported
- Amper doesn't watch the file system, so automatic reloads are only available when using the IDE


## Templates

In modularized projects, there is often a need to have a certain common configuration for all or some modules.
Typical examples could be a testing framework used in all modules or a Kotlin language version.

Amper offers a way to extract whole sections or their parts into reusable template files. These files are named `<name>.module-template.yaml` and have the same structure as `module.yaml` files. Templates could be applied to any `module.yaml` in the `apply:` section.

E.g. `module.yaml`:
```yaml
product: jvm/app

apply: 
  - ../common.module-template.yaml
```

../common.module-template.yaml:
```yaml
test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.10

settings:
  kotlin:
    languageVersion: 1.8
```

Sections in the template can also have `@platform`-qualifiers. See the [Multiplatform configuration](#multiplatform-configuration) section for details.

> Template files can't have `product:` and `apply:` sections. That is, templates can't be recursive and can't define product lists.

Templates are applied one by one, using the same rules as [platform-specific dependencies and settings](#dependencysettings-propagation):
- Scalar values (strings, numbers etc.) are overridden.
- Mappings and lists are appended.

Settings and dependencies from the `module.yaml` file are applied last. The position of the `apply:` section doesn't matter, the `module.yaml` file content always has precedence E.g.

`common.module-template.yaml`:
```yaml
dependencies:
  - ../shared

settings:
  kotlin:
    languageVersion: 1.8
  compose: enabled
```

`module.yaml`:
```yaml
product: jvm/app

apply:
  - ./common.module-template.yaml

dependencies:
  - ../jvm-util

settings:
  kotlin:
    languageVersion: 1.9
  jvm:
    release: 8
```

After applying the template the resulting effective module is:
`module.yaml`:
```yaml
product: jvm/app

dependencies:  # lists appended
  - ../shared
  - ../jvm-util

settings:  # objects merged
  kotlin:
    languageVersion: 1.9  # module.yaml overwrites value
  compose: enabled        # from the template
  jvm:
    release: 8   # from the module.yaml
```

## Gradle-based projects

> Gradle 8.7+ is required to use the Amper plugin, Gradle 8.11.1 is recommended.

In a Gradle-based project, instead of a `project.yaml` file, you need a `settings.gradle.kts` file
and a `gradle/wrapper/` folder in the project root:
```
|-gradle/...
|-src/
|  |-...
|-module.yaml
|-settings.gradle.kts
```

In case of a multi-module projects, the `settings.gradle.kts` should be placed in the root as usual:

```
|-app/
|  |-...
|  |-module.yaml
|-lib/
|  |-...
|  |-module.yaml
|-settings.gradle.kts
```

The Amper plugin needs to be added in the `settings.gradle.kts` and Amper modules explicitly specified:

```kotlin
pluginManagement {
    // Configure repositories required for the Amper plugin
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        maven("https://packages.jetbrains.team/maven/p/amper/amper")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}

plugins {
    // Add the plugin
    id("org.jetbrains.amper.settings.plugin").version("0.7.0-dev-2642")
}

// Add Amper modules to the project
include("app", "lib")
```

### Gradle interop

The Gradle interop supports the following scenarios:

* partial use of Amper in an existing Gradle project,
* smooth and gradual [migration of an existing Gradle project](./GradleMigration.md) to Amper,
* writing custom Gradle tasks or using existing Gradle plugins in an existing Amper module.

Gradle features supported in Amper modules:
* Cross-project dependencies between Gradle sub-projects and Amper modules.
* Using the default [libs.versions.toml version catalogs](https://docs.gradle.org/current/userguide/platforms.html#sub:conventional-dependencies-toml). 
* Writing Gradle [custom tasks](#writing-custom-gradle-tasks).
* Using [Gradle plugins](#using-gradle-plugins).
* Configuring additional [settings in the `build.gradle.kts` files](#configuring-settings-in-the-gradle-build-files).
* [Gradle-compatible file layout](#file-layout-with-gradle-interop).

To use Gradle interop in an Amper module, place either a `build.gradle.kts` or a `build.gradle` file next to
your `module.yaml` file:
```
|-src/
|  |-main.kt
|-module.yaml
|-build.gradle.kts
```

#### Writing custom Gradle tasks

As an example let's use the following `module.yaml`:
```yaml
product: jvm/app
```

Here is how to write a custom task in the `build.gradle.kts`:
```kotlin
tasks.register("hello") {
    doLast {
        println("Hello world!")
    }
}
```
Read more on [writing Gradle tasks](https://docs.gradle.org/current/userguide/more_about_tasks.html).


#### Using Gradle plugins

It's possible to use any existing Gradle plugin, e.g. a popular [SQLDelight](https://cashapp.github.io/sqldelight/2.0.0/multiplatform_sqlite/):
```kotlin
plugins { 
    id("app.cash.sqldelight") version "2.0.0"
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("com.example")
        }
    }
}
```

> The following plugins are preconfigured and their versions can't be changed:

| Plugin                                      | Version |
|---------------------------------------------|---------|
| `org.jetbrains.kotlin.multiplatform`        | 2.1.20  |
| `org.jetbrains.kotlin.android`              | 2.1.20  |
| `org.jetbrains.kotlin.plugin.serialization` | 2.1.20  |
| `com.android.library`                       | 8.2.0   |
| `com.android.application`                   | 8.2.0   |
| `org.jetbrains.compose`                     | 1.6.10  |

Here is how to use these plugins in a Gradle script:
```kotlin
plugins {
    kotlin("multiplatform")     // don't specify a version here,
    id("com.android.library")   // here,
    id("org.jetbrains.compose") // and here
}
```

#### Configuring settings in the Gradle build files

You can change all Gradle project settings in Gradle build files as usual. Configuration in `build.gradle*` file has
precedence over `module.yaml`. That means that a Gradle script can be used to tune/change the final configuration of your
Amper module.

E.g., the following Gradle script configures the working dir and the `mainClass` property: 
```kotlin
application {
    executableDir = "my_dir"
    mainClass.set("my.package.Kt")
}
```

#### Configuring C-interop using the Gradle build file

Use the following configuration to add C-interop in a Gradle-based Amper project:

```kotlin
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

kotlin {
  targets.filterIsInstance<KotlinNativeTarget>().forEach {
    it.compilations.getByName("main").cinterops {
      val libcurl by creating {
        // ...
      }
    }
  }
}
```

Read more about C-interop configuration in
the [Kotlin/Native documentation](https://kotlinlang.org/docs/native-app-with-c-and-libcurl.html#add-interoperability-to-the-build-process).

#### File layout with Gradle interop

The default [module layout](#project-layout) suites best for the newly created modules:  
```
|-src/
|  |-main.kt
|-resources/
|  |-...
|-test/
|  |-test.kt
|-testResources/
|  |-...
|-module.yaml
|-build.gradle.kts
```

For migration of an existing Gradle project, there is a compatibility mode (see also [Gradle migration guide](GradleMigration.md)).
To set the compatibility mode, add the following snippet to a `module.yaml` file:
```yaml
module:
  layout: gradle-kmp  # may be 'default', 'gradle-jvm', `gradle-kmp`
```

Here are possible layout modes:
 - `default`: Amper ['flat' file layout](#project-layout) is used. Source folders configured in the Gradle script are not available.  
 - `gradle-jvm`: the file layout corresponds to the standard Gradle [JVM layout](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html). Additional source sets configured in the Gradle script are preserved.
 - `gradle-kmp`: the file layout corresponds to the [Kotlin Multiplatform layout](https://kotlinlang.org/docs/multiplatform-discover-project.html#source-sets). Additional source sets configured in the Gradle script are preserved.

See the [Gradle and Amper layouts comparison](#gradle-vs-amper-project-layout).

E.g., for the `module.yaml`:
```yaml
product: jvm/app

module:
  layout: gradle-jvm
```

The file layout is:
```
|-src/
|  |-main/
|  |  |-kotlin
|  |  |  |-main.kt
|  |  |-resources
|  |  |  |-...
|  |-test/
|  |  |-kotlin
|  |  |  |-test.kt
|  |  |-resources
|  |  |  |-...
|-module.yaml
|-build.gradle.kts
```

While for the `module.yaml`:
```yaml
product: jvm/app

module:
  layout: gradle-kmp
```

The file layout is:
```
|-src/
|  |-commonMain/
|  |  |-kotlin
|  |  |  |-...
|  |  |-resources
|  |  |  |-...
|  |-commonTest/
|  |  |-kotlin
|  |  |  |-...
|  |  |-resources
|  |  |  |-...
|  |-jvmMain/
|  |  |-kotlin
|  |  |  |-main.kt
|  |  |-resources
|  |  |  |-...
|  |-jvmTest/
|  |  |-kotlin
|  |  |  |-test.kt
|  |  |-resources
|  |  |  |-...
|-module.yaml
|-build.gradle.kts
```

In the compatibility mode source sets could be configured or amended in the Gradle script. They are accessible by their names: `commonMain`, `commonTest`, `jvmMain`, `jvmTest`, etc.:
```kotlin
kotlin {
    sourceSets {
        // configure an existing source set
        val jvmMain by getting {
            // your configuration here
        }
        
        // add a new source set
        val mySourceSet by creating {
            // your configuration here
        }
    }
}
```

Additionally, source sets are generated for each [alias](#aliases). E.g. given the following module configuration:

```yaml
product:
  type: lib
  platforms: [android, jvm]
  
module:
  layout: gradle-kmp

aliases:
  - jvmAndAndroid: [jvm, android]
```

two source sets are generated: `jvmAndAndroid` and `jvmAndAndroidTest` and can be used as following:

```kotlin
kotlin {
    sourceSets {
        val jvmAndAndroid by getting {
            // configure the main source set
        }
        
        val jvmAndAndroidTest by getting {
            // configure the test source set
        }
    }
}
```

#### Gradle vs Amper project layout

Here is how Gradle layout maps to the Amper file layout:

| Gradle JVM         | Amper         |
|--------------------|---------------|
| src/main/kotlin    | src           |
| src/main/java      | src           |
| src/main/resources | resources     |
| src/test/kotlin    | test          |
| src/test/java      | test          |
| src/test/resources | testResources |

| Gradle KMP                      | Amper            |
|---------------------------------|------------------|
| src/commonMain/kotlin           | src              |
| src/commonMain/java             | src              |
| src/commonMain/composeResources | composeResources |
| src/jvmMain/kotlin              | src@jvm          |
| src/jvmMain/java                | src@jvm          |
| src/jvmMain/resources           | resources@jvm    |
| src/commonTest/kotlin           | test             |
| src/commonTest/java             | test             |


See also documentation on [Kotlin Multiplatform source sets](https://kotlinlang.org/docs/multiplatform-discover-project.html#source-sets) and [custom source sets configuration](https://kotlinlang.org/docs/multiplatform-dsl-reference.html#custom-source-sets).


## Brief YAML reference
YAML describes a tree of mappings and values. Mappings have key-value pairs and can be nested. Values can be scalars (string, numbers, booleans) and sequences (lists, sets).
YAML is indent-sensitive.

Here is a [cheat-sheet](https://quickref.me/yaml.html) and [YAML 1.2 specification](https://yaml.org/spec/1.2.2/). 

Strings can be quoted or unquoted. These are equivalent: 
```yaml
string1: foo bar
string2: "foo bar"
string3: 'foo bar'
```

Mapping:
```yaml
mapping-name:
  field1: foo bar
  field2: 1.2  
```

List of values (strings):
```yaml
list-name:
  - foo bar
  - "bar baz"  
```

List of mapping:
```yaml
list-name:
  - named-mapping:
      field1: x
      field2: y
  - field1: x
    field2: y
```
