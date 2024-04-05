## Before you start

Check the [setup instructions](Setup.md), and open [a new project template](../examples/new-project-template) in the IDE to make sure everything works.

## Basics

Amper exists as a standalone build tool, and also as a Gradle plugin.
The [Gradle-based Amper](#gradle-based-projects) supports full Gradle interop and can be used in existing Gradle
projects. Certain functionality and behavior might differ between the standalone Amper and the Gradle-based amper.

An Amper **project** in is defined by a `project.yaml` file. This file contains the list of modules and the project-wide
configuration. The folder with the `project.yaml` file is the project root. Modules can only be located under the
project root. If there is only one module in the project, `project.yaml` file is not required.

An Amper **module** is a directory with a `module.yaml` configuration file, module sources and resources.
A *module configuration file* describes _what_ to produce: e.g. a reusable library or a platform-specific application.
Each module describes a single product. Several modules can't share same sources or resources.

> Amper currently users YAML as a configuration language, here is a [brief intro YAML](#brief-yaml-reference).
> YAML is not the final language choice, and we are working on an alternative option._

_How_ to produce the desired product, that is, the build rules, is the responsibility of the Amper build engine
and [extensions](#extensibility).
In a Gradle-based Amper it's possible to use [plugins](Documentation.md#using-gradle-plugins) and
write [custom Gradle tasks](#writing-custom-gradle-tasks).

Amper supports Kotlin Multiplatform as a core concept and offers a special syntax to deal with multi-platform
configuration. There is a dedicated [**@platform-qualifier**](#platform-qualifier) used to mark platform-specific code,
dependencies, settings, etc. You'll see it in the examples below.

## Project layout

Basic single-module Amper project looks like this:

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
|-project.jaml
```

The `project.yaml` file:

```yaml
modules:
  - ./app
  - ./lib
```

In a Gradle-based project the `settings.gradle.kts` is expected instead of a `project.yaml` file.
And it is always required. Read more in the [Gradle-based projects](Documentation.md#gradle-based-projects) section.
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

By convention a `main.kt` file if present is a default entry point for the application.
Read more on [configuring the application entry points](#configuring-entry-points).

In a JVM module you can mix Kotlin and Java code:
```
|-src/             
|  |-main.kt      
|  |-Util.java      
|-module.yaml
```

In a [multi-platform module](#multi-platform-projects) platform-specific code is located in the folders
with [`@platform`-qualifier](#platform-qualifier):
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

> In the future, we plan to also support a more light-weight multi-platform layout like the one below:
 
```
|-src/             # common and platform-specific code
|  |-main.kt      
|  |-util.kt       #  API with ‘expect’ part
|  |-util@ios.kt   #  API implementation with ‘actual’ part for iOS
|  |-util@jvm.kt   #  API implementation with ‘actual’ part for JVM
|-module.yaml
```

Several modules can't share sources and resources. This ensures that the IDE always knows how to analyze and
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

A `module.yaml` file has several main sections: `product:`, `dependencies:` and `settings:`. A module could produce a
single product, such as a reusable library or an application. Read more on the [supported product types](#product-types)

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

- `lib` - a reusable Amper library which could be used as dependency by other modules in the Amper project.
- `jvm/app` - a JVM console or desktop application
- `windows/app` - a mingw64 app
- `linux/app` - a native linux application
- `macos/app` - a native macOS application
- `android/app` - an Android VM application  
- `ios/app` - an iOS/iPadOS application

Other product types what we plan to support in the future:

- `watchos/app` - an Apple Watch application (not yet implemented)
- `windows/dll`
- `linux/so`
- `macos/framework`
- etc.

### Packaging

Each product has an associated packaging, defined by the target OS and product type. E.g. `macos/app` are packaged as
so-called bundles, `android/app` as APKs, and `jvm/app` as jars.
By default, packages are generated according to platform conventions. For custom packaging configuration Amper will
offer a separate `packaging:` section.

> Packaging configuration is not yet implemented. Meanwhile, you can use [Gradle interop](#gradle-interop) for
> custom packaging.

```yaml
product: jvm/app

packaging:
  - type: fatJar            # specify a type of the package 
    content: # specify how to lay out the final artifact
      include: licenses/**    
```

### Publishing

Publishing means preparing the resulting [package](#packaging) for external use, and optionally uploading or deploying
it.
Here are a few examples of publishing:
- Preparing a JVM jar or a KLIB with sources and docs and uploading them Maven Central or to another Maven repository.
- Creating a CocoaPods package and publishing it for use in Xcode projects.
- Preparing Android AABs or Apple IPAs for publishing into App Stores. 
- Preparing and signing an MSI, DMG, or DEB distributions

> Publishing configuration is not yet implemented. Meanwhile, you can use [Gradle interop](#gradle-interop)
> for custom publishing.

Here is a very rough approximation, how publishing could look like in Amper:

```yaml
product:
  type: lib
  platforms: [android, iosArm64, iosSimulatorArm64]

publishing:
  - type: maven        
    groupId: ...
    artifactId: ...
    repository:
      url: ...
      credentials: ...

  - type: cocoapods
    name: MyCocoaPod
    version: 1.0
    summary: ...
    homepage: ...
```

### Multi-platform configuration

`dependencies:` and `setting:` sections could be specialized for each platform using the `@platform`-qualifier.  An example of multi-platform library with some common and platform-specific code:
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
  - com.google.android.material:material:1.5.0

# These dependencies are available in iOS code only
dependencies@ios:
  - pod: 'Alamofire'
    version: '~> 2.0.1'

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
See [details on multi-platform configuration](#multi-platform-configuration) for more information.

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

The `app/module.yaml` could declare dependency on `ui/utils` as follows:

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

#### Native dependencies

> Native dependencies are not yet implemented.

To depend on a npm, CocoaPods, or a Swift package, use the following format:

```yaml
dependencies:
  - npm: "react"
    version: "^17.0.2"
```    

```yaml
dependencies:
  - pod: 'Alamofire'
    version: '~> 2.0.1'
```

```yaml
dependencies:
  - pod: 'Alamofire'
    git: 'https://github.com/Alamofire/Alamofire.git'
    tag: '3.1.1'
```

```yaml
dependencies:
  - swift-package:
      url: "https://github.com/.../some-package.git"
      from: "2.0.0"
      target: "SomePackageTarget"
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

> Currently only *.property files with credentials are supported.

**Note on Gradle interop**

If some repositories are defined in `settings.gradle.kts` using a `dependencyResolutionManagement` block, they are only
taken into account by pure Gradle subprojects, and don't affect Amper modules. If you want to define repositories in a
central place for Amper modules, you can use a `repositories` list in a [template
file](#templates) and apply this template to your modules.

Technical explanation: in Gradle, adding any repository at the subproject level will by default discard the repositories
configured in the settings (unless a different Gradle
[RepositoriesMode](https://docs.gradle.org/current/javadoc/org/gradle/api/initialization/resolve/RepositoriesMode.html)
is used). Default repositories provided by Amper is an equivalent to adding a `repositories` section in
the `build.gradle.kts`file of each individual Amper module.

### Dependency/Version Catalogs

There are several types of dependency catalogs that are available in Amper:
- Dependency catalogs provided by toolchains (such as Kotlin, Compose Multiplatform etc.). The toolchain catalog names correspond to the [names of the toolchains in the settings section](#settings). E.g. dependencies for the Compose Multiplatform frameworks are accessible using the `$compose` catalog, and its settings using the `compose:` section.
- Gradle-based Amper supports Gradle version catalog in the default [gradle/libs.versions.toml file](https://docs.gradle.org/current/userguide/platforms.html#sub:conventional-dependencies-toml). Dependencies from this catalog can be accessed via `$libs.` catalog name according to the [Gradle name mapping rules](https://docs.gradle.org/current/userguide/platforms.html#sub:mapping-aliases-to-accessors). 
- User-defined dependency catalogs (not yet implemented).

All supported catalogs could be accessed via a `$<catalog-name.key>` reference, for example:
```yaml
dependencies:
  - $compose.material3    # dependency from a Compose Multiplatform catalog
  - $my-catalog.ktor      # dependency from a custom project catalog with a name 'my-catalog' 
  - $libs.commons.lang3   # dependency from a Gradle default libs.versions.toml catalog
```

Dependencies from catalogs may have [scope and visibility](#scopes-and-visibility):

```yaml
dependencies:
  - $compose.foundation: exported
  - $my-catalog.db-engine: runtime-only 
```

### Settings

The `settings:` section contains toolchains settings. _Toolchain_ is an SDK (Kotlin, Java, Android, iOS) or a simpler tool (like linter, code generator). Currently, the following toolchains are supported: `kotlin:`, `java:`, `android:`, `compose:`.

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

See [multi-platform settings configuration](#multi-platform-settings) for more details.

#### Configuring Kotlin Serialization

To enable the [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization), use the following snippet:
```yaml
settings:
  kotlin:
    serialization: json  # JSON or other format
```
This snippet configures compiler and runtime settings, including a dependency on the JSON library.

In its full form it looks like this:
```yaml
settings:
  kotlin:
    serialization: 
      format: json
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

# Android and iOS build require more memory
org.gradle.jvmargs=-Xmx4g
```

#### Configuring entry points

##### JVM
By convention a single `main.kt` file (case-insensitive) in the source folder is a default entry point for the application.

Here is how to specify the entry point explicitly for JVM:
```yaml
product: jvm/app

settings:
  jvm:
    mainClass: org.example.myapp.MyMainKt
```
##### Native
By convention a single `main.kt` file (case-insensitive) in the source folder is a default entry point for the application.

##### Android

Application entry point is specified in the AndroidManifest.xml file according to the [official Android documentation](https://developer.android.com/guide/topics/manifest/manifest-intro).
```
|-src/ 
|  |-MyActivity.kt
|  |-AndroidManifest.xml
|  |-... 
|-module.yaml
```

src/AndroidManifest.xml:
```xml
<manifest ... >
  <application ... >
    <activity android:name="com.example.myapp.MainActivity" ... >
    </activity>
  </application>
</manifest>
```

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

#### Special types of tests

Unit tests are an integral part of a module. In addition to unit tests, some platforms have additional types of tests,
such as Android [instrumented tests](https://developer.android.com/training/testing/instrumented-tests)
or [iOS UI Test](https://developer.apple.com/documentation/xctest/user_interface_tests). Also, a project might need
dedicated benchmarking, performance or integration tests.

To keep module configuration files simple and at the same to offer flexibility for a different type of tests,
Amper has a concept of _auxiliary modules_.

> Auxiliary modules are not yet implemented.

They differ from the regular modules in some important aspects:

- Auxiliary module is located inside its main module.
- There may be multiple Auxiliary modules for a single main module.
- Auxiliary module have an implicit dependency on its main module.
- Auxiliary module is a _friend_ to its main module and can see the internal declarations which are often needed for
  white-box of grey-box testing.
- Auxiliary module inherits settings from its main module.
- Main module cannot depend on its Auxiliary module in `dependencies:` section, but can in `test-dependencies:` section.
- Auxiliary module is not accessible from outside its main module, so other modules can't depend on Auxiliary modules.

You may think of module's unit tests which are located in `test/` folder and have dedicated `test-dependencies:`
and `test-settings:` as auxiliary modules, which are embedded directly into the main module for the convenience.

#### Android Instrumented tests
Here is how Android Instrumented tests could be added as an Auxiliary module:

Main module.yaml:
```yaml
product: android/app

dependencies:
  - io.ktor:ktor-client-core:2.2.0

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.10
  
settings: 
  kotlin:
    languageVersion: 1.8
```

Auxiliary instrumented-tests/module.yaml:
```yaml
product: android/instrumented-tests

dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.10
  - androidx.test.uiautomator:uiautomator:2.3.0
  
settings: 
  android:
    testInstrumentationRunnerArguments:
      clearPackageData: true
```
   
And organize files as following:
```
|-src/             
|  |-MyMainActivity.kt      
|-test/   
|  |-MyUnitTest.kt
|-instrumented-tests
|  |-src/
|  |  |-MyInstrumentedTest.kt 
|  |-module.yaml 
|-module.yaml
```
                    
#### Sharing test utilities
The test utility code (such as test fixtures) could be shared between Unit and Instrumented tests.

Main module.yaml:
```yaml
product: android/app

dependencies:
  - io.ktor:ktor-client-core:2.2.0

test-dependencies:
  - ./test-utils

settings:
  kotlin:
    languageVersion: 1.8
```

Auxiliary test-utils/module.yaml
```yaml
product: lib

dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.10: exported
```

Auxiliary instrumented-tests/module.yaml:
```yaml
product: android/instrumented-tests

dependencies:
  - ../test-utils
  - androidx.test.uiautomator:uiautomator:2.3.0

settings:
  android:
    testInstrumentationRunnerArguments:
      clearPackageData: true
```

Here is the file layout:

```
|-src/             
|  |-MyMainActivity.kt      
|-test/   
|  |-MyUnitTest.kt
|-test-utils/
|  |-src/
|  |  |-MyAssertions.kt 
|  |-module.yaml 
|-instrumented-tests/
|  |-src/
|  |  |-MyInstrumentedTest.kt 
|  |-module.yaml 
|-module.yaml
```

## Resources

Files placed into the `resources` folder are copied to the resulting products:
```
|-src/             
|  |-...
|-resources/     # These files are copied into the final products
|  |-...
```

In [multi-platform modules](#multi-platform-configuration) resources are merged from the common folders and corresponding platform-specific folders:
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
There are tree distinct scenarios where such an interoperability is needed:

- Consuming: Kotlin code can use APIs from existing platform libraries, e.g. jars on JVM or CocoaPods on iOS.  
- Publishing: Kotlin code can be compiled and published as platform libraries to be consumed by the target platform's tooling; such as jars on JVM, *.so on linux or frameworks on iOS.    
- Joint compilation: Kotlin code be compiled and linked into a final product together with the platform languages, like JVM, C, Objective-C and Swift.

> Kotlin JVM supported all these scenarios from the beginning.
> However, full interoperability is currently not supported in the Kotlin Native.

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

Joint compilation is already supported for Java and Kotlin, and in future Kotlin Native will also support joint Kotlin+Swift compilation.

From the user's point of view the joint compilation is transparent; they could simply place the code written in different languages into the same source folder:

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

## Multi-platform projects

### Platform qualifier

Use the `@platform`-qualifier to mark platform-specific source folders and sections in the module.yaml files. 
You can use Kotlin Multiplatform [platform names](https://kotlinlang.org/docs/native-target-support.html) and families as `@platform`-qualifier.
```yaml
dependencies:                   # common dependencies for all platforms
dependencies@ios:               # ios is a platform family name  
dependencies@iosArm64:          # iosArm64 is a KMP platform name
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


Here is the partial list of platform and families:
```yaml
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
  ...
```

Common code is visible from `@platform`-specific code, but not vice versa:
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

For [Kotlin Multiplatform expect/actual declarations](https://kotlinlang.org/docs/multiplatform-connect-to-apis.html), put your `expected` declarations into the `src/` folder, and `actual` declarations into the corresponding `src@<platform>/` folders. 

#### Aliases

Also, you can share code between several platforms by using custom `aliases:`

```yaml
product:
  type: lib
  platforms: [iosArm64, android, jvm]

aliases:
  - jvmAndAndroid: [jvm, android]

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

### Multi-platform dependencies

When you use a Kotlin Multiplatform library, its platforms-specific parts are automatically configured for each module platform.

Example:
To add a [KmLogging library](https://github.com/LighthouseGames/KmLogging) to a multi-platform module, simply write

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
### Multi-platform settings

All toolchain settings, even platform-specific could be placed in the `settings:` section:
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

There are situations, when you need to override certain settings in for a specific platform only. You can use `@platform`-qualifier. 

Note that certains platforms names match the toolchin names, e.g. iOS and Android:
- `settings@ios` qualifier specializes settings for all iOS target platforms,
- `settings:ios:` is a iOS toolchain settings   

This could lead to confusion in cases like:
```yaml
product: ios/app

settings@ios:    # settings to be used for iOS target platform
  ios:           # iOS toolchain settings
    deploymentTarget: 17
  kotlin:        # Kotlin toolchain settings
    languageVersion: 1.8
```
Luckily, there should rarely be a need to such configuration.
We also plan to address this by linting with conversion to a more readable form:   
```yaml
product: ios/app

settings:
  ios:           # iOS toolchain settings
    deploymentTarget: 17
  kotlin:        # Kotlin toolchain settings
    languageVersion: 1.8
```

For settings with the `@platform`-qualifiers the [propagation rules](#dependencysettings-propagation) apply. E.g., for the given configuration:
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
- Scalar values (strings,  numbers etc.) are overridden by more specialized `@platform`-sections.
- Mappings and lists are appended.

Think of the rules like adding merging Java/Kotlin Maps.

## Build variants

In the native world, it's rather common to have at least two types of build configurations, release and debug.
In the release configuration settings like compiler optimizations and obfuscation are enabled, while in the debug mode they are disabled and additional debug information is generated.

In the Android world, in addition to
the [debug and release build types](https://developer.android.com/build/build-variants#build-types), there exists a
concept of [product flavors](https://developer.android.com/build/build-variants#product-flavors). Product flavor is a
slight modification of the final product, e.g. with paid features or ads for a free version. Product flavors are also
used for [white labeling](https://en.wikipedia.org/wiki/White-label_product), e.g. to add a logo or certain resources to
the application without modify the code.

To support such configurations, Amper offers a concept of _build variants_. A build variant can have additional code,
resources, override/append dependencies and settings.

> Build variants are not yet fully implemented.

Here is how a basic build variants configuration looks like:
```yaml
product: android/app

# define two variants
variants: [debug, release] 

dependencies:
  - io.ktor:ktor-client-core:2.3.0
    
dependencies@debug:
  - org.jetbrains.compose.ui:ui-android-debug:1.0.0
  
settings:
  kotlin:
    languageVersion: 1.8

settings@debug:
  kotlin:
    debug: true
```

And the basic file layout could look like this:
```
|-src/
|  |-main.kt               
|-src@debug/
|  |-debugUtil.kt
|-module.yaml 
```

You might have noticed that build variants configuration uses the `@variant`-qualifier, similarly to
the [`@platform`-qualifier](#platform-qualifier). Also, the same rule as
with [platform-specific sections](#multi-platform-projects) apply to the build variants.
        

#### Advanced build variants

To model both Android build types and Android product flavors, multidimensional build variants are supported:

```yaml
product: android/app

variants:
  - [debug, release]
  - [paid, free]

dependencies:
  - ...

dependencies@paid:
  - ...

dependencies@debug:
  - ...
```
   
With a possible file layout:
```
|-src/
|  |-main.kt               
|-src@debug/
|  |-debugUtil.kt
|-src@paid/
|  |-PaidFeature.kt
|-src@free/
|  |-AdsUtil.kt
|-module.yaml 
```

#### Build variants and Multiplatform

Build variants can be combined with multi-platform configuration as well. Amper offers a special `@platform+variant`-notation:

```yaml
product:
  type: lib
  platforms: [android, iosArm64, iosSimulatorArm64]

variants: [debug, release]

dependencies:
  - io.ktor:ktor-client-core:2.3.0

dependencies@android:
  - com.google.android.material:material:1.5.0

# add debug utility to Android code in debug build variant     
dependencies@android+debug:
  - org.jetbrains.compose.ui:ui-android-debug:1.0.0

settings:
  kotlin:
    languageVersion: 1.8

# Set Kotlin settings specific to iOS code: 
settings@ios:
  kotlin:
    linkerOptions: [...]

# Set Kotlin debug mode for debug build variants for both platforms 
settings@debug:
  kotlin:
    debug: true
```

Platforms and variants in the file layout:
```
|-src/                 # common code for iOS and Android
|-src@ios/             # iOS-speific code, sees declaration from src/
|-src@android/         # Android-speific code, sees declaration from src/
|-src@debug/           # debug-only code, sees declaration from src/
|-src@android+debug/   # Android-specific debug-only code, sees declaration from src/, src@android/ and src@debug/ 
|-module.yaml 
```

## Templates

In modularized projects, there is often a need to have a certain common configuration for some or all or some modules.
Typical examples could be a testing framework used in all modules or a Kotlin language version.

Amper offers a way to extract whole sections or their parts into reusable template files. These files are named `<name>.module-template.yaml` and have same structure as `module.yaml` files. Templates could be applied to any module.yaml in the `apply:` section.

E.g. module.yaml:
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

Sections in the template can also have `@platform`-qualifiers. See the [Multi-platform configuration](#multi-platform-configuration) section for details.

> Template files can't have `product:` and `apply:` sections. That is, templates can't be recursive. Templates can't
> define product lists.

Templates are applied one by one, using the same rules as [platform-specific dependencies and settings](#dependencysettings-propagation):
- Scalar values (strings,  numbers etc.) are overridden.
- Mappings and lists are appended.

Settings and dependencies from the module.yaml file are applied last. Position of the `apply:` section doesn't matter, o module.yaml file always have precedence E.g.

common.module-template.yaml:
```yaml
dependencies:
  - ../shared

settings:
  kotlin:
    languageVersion: 1.8
  compose: enabled
```

module.yaml:
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
module.yaml:
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

## Extensibility

> Extensibility is not yet implemented in a standalone Amper. Meanwhile, you can use [Gradle interop](#gradle-interop)
> for plugins and custom
> tasks.

The main design goal for Amper is simplicity and ease of use specifically for Kotlin and Kotlin Multiplatform.
We would like to provide great user experience of the box. That's why there are many aspects that are available in Amper as first-class citizens. 
Streamlined [multiplatform](#multi-platform-configuration) setup,
built-in support for [CocoaPods dependencies](#native-dependencies),
straightforward [Compose Multiplatform configuration](#configuring-compose-multiplatform), etc.,
should enable easy onboarding and quick start. Nevertheless, as projects grow, Kotlin ecosystem expands, and more use
cases emerge,
its inevitable that some level of extensibility will be needed. 

The following aspects are designed to be extensible:
- [Product types](#product-types) - an extension could provide additional product types, such as a Space or a Fleet plugin, an application server app, etc.   
- [Publishing](#publishing) - there might be need to publish to, say, vcpkg or a specific marketplace, which are not supported out of the box.  
- [External Dependency](#native-dependencies) - similarly to publishing, there might be need to consume dependencies from vcpkg or other package managers.
- [Toolchains](#settings) - toolchains are the main actors in the build pipeline extensibility - they provide actual build logic for linting, code generation, compilation, obfuscation.  

Extensions are supposed to contribute to the DSL using declarative approach (e.g. via schemas),
and also implement the actual logic with regular imperative language (e.g. Kotlin). Such a mixed approach should allow for fast project structure evaluation and flexibility at the same time. 

Below is a very rough approximation of a possible toolchain extension:

```yaml
product: jvm/app

settings: 
  my-source-processor:
    replace: "${author}"
    with: Me 
```

With a convention file layout:
```
|-src/
|  |-main.kt
|-module-extensions/
|  |-my-source-processor/
|  |  |-module-extension.yaml   # generated or manually created extension's DSL schema 
|  |  |-extension.kt            # implementation
|-module.yaml 
```

And extension.kt code:
```kotlin
class MySourceProcessor : SourceProcessorExtension {
    val replace : StringParameter
    val with: StringParameter
    
    override fun process(input : SourceInput, output: SourceOutput) {
        val replaced = input.readText().replaceAll(replace, with)
        output.writeText(replaced)
    }
}
```

The Amper engine would be able to quickly discover the DSL schema for `setting:my-source-processor:` when evaluatig the project structure. And also compiler and execute arbitrary login defined in Kotlin file.     

## Gradle-based projects

> Gradle-based Amper is tested with Gradle 8.1.

In a Gradle-based Amper you need a `settings.gradle.kts` file and a `gradle/wrapper/` folders in the project root:
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

Amper needs to be added in the `settings.gradle.kts` and Amper modules explicitly specfied:

```kotlin
pluginManagement {
    // Configure repositories required for the Amper plugin
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/amper/amper")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}

plugins {
    // Add the plugin
    id("org.jetbrains.amper.settings.plugin").version("0.3.0-dev-487")
}

// add Amper modules to the project
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

It's possible to use any existing a Gradle plugin, e.g. a popular [SQLDelight](https://cashapp.github.io/sqldelight/2.0.0/multiplatform_sqlite/):
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
| `org.jetbrains.kotlin.multiplatform`        | 1.9.20  |
| `org.jetbrains.kotlin.android`              | 1.9.20  |
| `org.jetbrains.kotlin.plugin.serialization` | 1.9.20  |
| `com.android.library`                       | 8.1.0   |
| `com.android.application`                   | 8.1.0   |
| `org.jetbrains.compose`                     | 1.5.10  |

Here is how to use these plugins in a Gradle script:
```kotlin
plugins {
  kotlin("multiplatform")     // don't specify a version here,
    id("com.android.library")   // here,
    id("org.jetbrains.compose") // and here
}
```

#### Configuring settings in the Gradle build files

You can change all Gradle project settings in gradle build files as usual. Configuration in `build.gradle*` file has
precedence over `module.yml`. That means that a Gradle script can be used to tune/change the final configuration of your
Amper module.

E.g., the following Gradle script configures the working dir and the `mainClass` property: 
```kotlin
application {
    executableDir = "my_dir"
    mainClass.set("my.package.Kt")
}
```

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
To set the compatibility mode, add the following snippet to a module.yaml file:
```yaml
module:
  layout: gradle-kmp  # may be 'default', 'gradle-jvm', `gradle-kmp`
```

Here are possible layout modes:
 - `default`: Amper ['flat' file layout](#project-layout) is used. Source folders configured in the Gradle script are not available.  
 - `gradle-jvm`: the file layout corresponds to the standard Gradle [JVM layout](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html). Additional source sets configured in the Gradle script are preserved.
 - `gradle-kmp`: the file layout corresponds to the [Kotlin Multiplatform layout](https://kotlinlang.org/docs/multiplatform-discover-project.html#source-sets). Additional source sets configured in the Gradle script are preserved.

See the [Gradle and Amper layouts comparison](#gradle-vs-amper-project-layout).

E.g., for the module.yaml:
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

While for the module.yaml:
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

| Gradle JVM         | Amper          |
|--------------------|---------------|
| src/main/kotlin    | src           |
| src/main/java      | src           |
| src/main/resources | resources     |
| src/test/kotlin    | test          |
| src/test/java      | test          |
| src/test/resources | testResources |

| Gradle KMP               | Amper          |
|--------------------------|---------------|
| src/commonMain/kotlin    | src           |
| src/commonMain/java      | src           |
| src/commonMain/resources | resources     |
| src/jvmMain/kotlin       | src@jvm       |
| src/jvmMain/java         | src@jvm       |
| src/jvmMain/resources    | resources@jvm |
| src/test/kotlin          | test          |
| src/test/java            | test          |
| src/test/resources       | testResources |


See also documentation on [Kotlin Multiplatform source sets](https://kotlinlang.org/docs/multiplatform-discover-project.html#source-sets) and [custom source sets configuration](https://kotlinlang.org/docs/multiplatform-dsl-reference.html#custom-source-sets).


## Brief YAML reference
YAML describes a tree of mappings and values. Mappings have key-value paris and can be nested. Values can be scalars (string, numbers, booleans) and sequences (lists, sets).
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
