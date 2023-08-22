## Basics

**Pot** is a directory with a `Pot.yaml` manifest file, sources and resources, which are used to build a certain product.  A Pot manifest file describes _what_ to produce: either a reusable library or a platform-specific application.
_How_ to produce the desired artifact is responsibility of the build engine and extensions (work in progress).

Sources and resources can't be shared by several Pots.

_NOTE:_ 🍯 _The name 'Pot' is temporary. We intentionally avoid using the term 'module' to prevent confusion with existing terminology (Kotlin module, IntelliJ module etc.)._

The DSL supports Kotlin Multiplatform as a core concept, and offers a special syntax to deal with multi-platform configuration:
there is a dedicated **@platform-qualifier** used to mark platform-specific code, dependencies, settings etc. You'll see it in the examples below. 

## Project layout

The basic Pot layout looks like this:
```
|-src/             
|  |-main.kt      
|-resources/       
|  |-...
|-test/       
|  |-MainTest.kt 
|-Pot.yaml
```

By convention a single `main.kt` file (case-insensitive) in the source folder is a default entry point for the application.

_NOTE: In [a Gradle-based project](#gradle-based-projects) the settings.gradle.kts should be located in the project root:_
```
|-...
|-Pot.yaml
|-settings.gradle.kts
```

See  

In a JVM Pot you can mix Kotlin and Java code:
```
|-src/             
|  |-main.kt      
|  |-Util.java      
|-Pot.yaml
```

In a multi-platform Pot platform-specific code is located in the folders with `@platform`-qualifier:
```
|-src/             # common code
|  |-main.kt      
|  |-util.kt       #  API with ‘expect’ part
|-src@ios/         # code to be compiled only for iOS targets
|  |-util.kt       #  API implementation with ‘actual’ part for iOS
|-src@jvm/         # code to be compiled only for JVM targets
|  |-util.kt       #  API implementation with ‘actual’ part for JVM
|-Pot.yaml
```

_NOTE: In the future we plan to also support a 'flat' multi-platform layout like the one below.
It requires some investment in the IntelliJ platform, so we haven't yet done it._ 
 
```
|-src/             # common and platform-specific code
|  |-main.kt      
|  |-util.kt       #  API with ‘expect’ part
|  |-util@ios.kt   #  API implementation with ‘actual’ part for iOS
|  |-util@jvm.kt   #  API implementation with ‘actual’ part for JVM
|-Pot.yaml
```

_NOTE: Sources and resources can't be shared by several Pots._
This is to make sure that a given source file is always present in a single analysis/resolve/refactoring context (that is, has a single well-defined set of dependencies and compilation settings).

## Pot Manifest file anatomy

`Pot.yaml` is a Pot manifest file and is declared using YAML (here is a [brief intro YAML](#brief-yaml-reference)).

_NOTE: YAML is not the final language choice. For the purpose of the prototyping and designing it serves well, but we plan to re-evaluate other options in the future._

A `Pot.yaml` file has several main sections: `product:` (or `products:`), `dependencies:` and `settings:`.  A pot could produce a single reusable library or multiple native platform-specific applications.

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

Product type describes the target platform and the type of the product at the same type. Here is the partial list of possible product types:
- `lib` - a reusable library which could be used as dependency by other Pots in the codebase.
- `jvm/app` - a JVM console or desktop application
- `windows/app` - a mingw64 app
- `linux/app`
- `macos/app`
- `android/app` - an Android VM application  
- `ios/app` - an iOS/iPadOS application
- `watchos/app` - an Apple Watch application (not yet implemented)

Other product types what we plan to support in the future:
- `windows/dll`
- `linux/so`
- `macos/framework`
- etc.

The product types is supposed to be [extensible](#extensibility), so the following types are also possible:
- `jvm/war`
- `jvm/intellij-plugin`

It's also possible to specify several products (not yet implemented), which can come handy for multi-platform applications, or interop with other build tools like Xcode:

A mobile application:
```yaml
products: 
  - app/ios
  - app/android

settings:
  kotlin:
    languageVersion: 1.9
```

A reusable library which could also be used as Framework in Xcode:
```yaml
products: 
  - type: lib
    platforms: [android]
    
  - type: ios/framework
```


### Packaging

Each product type has corresponding packaging, dictated by OS or environment. E.g. `macos/app` are packaged as so-called bundles, `android/app` as APKs, and `jvm/app` as jars.
By default, packages are generated according to platform and build tool conventions. When custom configuration is needed DSL offer a separate `packaging:` section.

_NOTE: Packaging configuration is not yet implemented_

```yaml
product: jvm/app

packaging:
  - product: jvm/app        # reference the product by type or ID
    package: fatJar         # specify type of the package 
    mainClass: my.app.Main  # specify an entry point 
    include: licenses/**    # and other things such as layout
```

Same also works for [multiple products](#product-types): 
```yaml
products:
  - ios/app
  - android/app

packaging:
  - product: ios/app        
    deploymentTarget: 15
    resources:
      include: "**/*.png"

  - product: android/app         
    applicationId: my.app.MyApp    
    # other android-specific packaging settings
```

### Publishing

_NOTE: Publishing is not yet designed or implemented._

Publishing means preparing the resulting [package](#packaging) for external use, and actually uploading or deploying it.
Here are a few examples of publishing:
- Preparing a JVM jar or a KLIB with sources and docs and uploading them Maven Central or to another Maven repository.
- Creating a CocoaPods package and publishing it for use in Xcode projects.
- Preparing and signing an MSI, DMG, or DEB distributions

Here is a very rough approximation, how publishing could look like in the DSL:

```yaml
products:
  - type: lib
    platforms: [android, iosArm64, iosSimulatorArm64]

  - ios/framework

publishing:
  - maven: lib             # reference the product by type or ID        
    groupId: ...
    artifactId: ...
    repository:
      url: ...
      credentials: ...

  - cocoapods: ios/framework # reference the product by type or ID
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
    compileSdkVersion: android-33

# We can add or override settings for specific platforms. 
# Let's override the Kotlin language version for iOS code: 
settings@ios:
  kotlin:
    languageVersion: 1.9 
```
See [details on multi-platform configuration](#multi-platform-configuration) for more information.

### Dependencies

#### External Maven dependencies

For Maven dependencies simply specify their coordinates:
```yaml
dependencies:
  - org.jetbrains.kotlin:kotlin-serialization:1.8.0
  - io.ktor:ktor-client-core:2.2.0
```

By default, Maven Central and Google Android repositories are pre-configured. To add extra repositories, use the following options: 

```yaml
repositories:
  - https://repo.spring.io/ui/native/release
  
  - id: jitpack
    url: https://jitpack.io
```

#### Internal dependencies
To depend on another Pot, use a relative path to the folder which contains the corresponding `Pot.yaml`. 
Internal dependency should start either with `./` or `../`.

Example: given the project layout
```
root/
  |-app/
  |  |-src/
  |  |-Pot.yaml
  |-ui/
  |  |-utils/
  |  |  |-src/
  |  |  |-Pot.yaml
```
The `app/Pot.yaml` could declare dependency on `ui/utils` as follows:
```yaml
dependencies:
  - ../ui/utils
```

Other examples of the internal dependencies:
```yaml
dependencies:
  - ./nested-folder-with-pot-yaml
  - ../sibling-folder-with-pot-yaml
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
In order to make a dependency visible to a dependent Pot, you need to explicitly mark it as `exported` (aka Gradle API-dependency). 

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
                        
_NOTE: Native dependencies are not yet implemented in the prototype._

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

### Version Catalogs

_NOTE: Version catalogs are not yet designed or implemented._

Here is how we think they could be used in the DSL:

```yaml
product: android/app

dependencies:
  - compose.foundation
  - compose.material

settings:
  compose:
     enabled: true
```

Catalogs might be provided by toolchains, defined by user or imported from [Gradle lib.version.toml](https://docs.gradle.org/current/userguide/platforms.html#sub:conventional-dependencies-toml) files.  

### Settings

The `settings:` section contains toolchains settings. _Toolchain_ is an SDK (Kotlin, Java, Android, iOS) or a simpler tool (like linter, code generator). Currently, the following toolchains are supported: `kotlin:`, `java:`, `android:`, `compose:`.
Toolchains list is supposed to be [extensible](#extensibility) in the future.

All toolchain settings are specified in the dedicated groups in the `settings:` section:
```yaml
settings: 
  kotlin:
    languageVersion: 1.8
    languageFeatures: [contextReceivers]
  android:
    compileSdkVersion: android-31
```

Here is the list of [currently supported toolchains and their settings](SettingsList.md).   

See [multi-platform settings configuration](#multi-platform-settings) for more details.


#### Configuring Compose Multiplatform

In order to enable [Compose](https://www.jetbrains.com/lp/compose-multiplatform/) (with a compiler plugin and required dependencies), add the following configuration:

JVM Desktop:
```yaml
product: jvm/app

dependencies:
  # add Compose dependencies
  - org.jetbrains.compose.desktop:desktop-jvm:1.4.1
    
settings: 
  # enable Compose toolchain
  compose:
    enabled: true
```

Android:
```yaml
product: android/app

dependencies:
  - org.jetbrains.compose.foundation:foundation:1.4.1
  - org.jetbrains.compose.material:material:1.4.1 

settings: 
  compose:
    enabled: true
```

_NOTE: Explicit dependency specification will be replaced with version catalog in future design. 
Also, certain dependencies might be added automatically when Compose is enabled._

_NOTE: For Compose Android and iOS you also need to set a couple if flags in `gradle.properties`:_
```properties
# Compose requires AndroidX
android.useAndroidX=true

# Android and iOS build require more memory
org.gradle.jvmargs=-Xmx4g
```

### Tests

Test code is located in the `test/` and `test@platform/` folders. Test settings and dependencies by default are inherited from the main configuration according to the [configuration propagation rules](#dependencysettings-propagation). 
To add test-only dependencies put them into `test-dependencies:` section. To add or override toolchain settings in tests, use `test-settings:` section.

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
|-Pot.yaml
```

```yaml
product:
  type: lib
  platforms: [android, iosArm64]

# these dependencies are available in main and test code
dependencies:
  - io.ktor:ktor-client-core:2.2.0

# add dependencies for test code
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

Unit tests is an integral part of a Pot manifest. In addition to unit tests, some platform have additional types of tests, such as Android [instrumented tests](https://developer.android.com/training/testing/instrumented-tests) or [iOS UI Test](https://developer.apple.com/documentation/xctest/user_interface_tests). Also project might need dedicated benchmarking, performance or integration tests.

In order to keep Pot Manifest files simple and at the same to offer flexibility for different type of tests, the DSL has a concept of _Auxiliary Pots_. Their main purpose is improving usability, and they differ from the regular Pot in some important aspects:
- Auxiliary Pot should be located in a subfolder inside its main Pot. 
- There may be multiple Auxiliary Pot for a single main Pot.
- Auxiliary Pot have an implicit dependency on its main Pot.
- Auxiliary Pot is a _friend_ to its main Pot and can see the internal declarations which are often needed for white-box of grey-box testing.
- Auxiliary Pot inherit settings from its main Pot.
- Main Pot cannot depend on its Auxiliary Pot in `dependencies:` section, but can in `test-dependencies:` section.
- Auxiliary Pot is not accessible from outside its main Pot, so other Pots can't depend on Auxiliary Pots.

You may think of tests which are located in `test/` folder and have dedicated `test-dependencies:` and `test-settings:` as Auxiliary Pots, which are embedded directly into the main Pot's DSL for the convenience.

_NOTE: Auxiliary Pots design is work in progress is not implemented in the prototype._

#### Android Instrumented tests
Here is how Android Instrumented tests could be added as an Auxiliary Pot:

Main Pot.yaml:
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

Auxiliary instrumented-tests/Pot.yaml:
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
|  |-Pot.yaml 
|-Pot.yaml
```
                    
#### Sharing test utilities
The test utility code (such as test fixtures) could be shared between Unit and Instrumented tests.

Main Pot.yaml:
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

Auxiliary test-utils/Pot.yaml
```yaml
product: lib

dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.10: exported
```

Auxiliary instrumented-tests/Pot.yaml:
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
|  |-Pot.yaml 
|-instrumented-tests/
|  |-src/
|  |  |-MyInstrumentedTest.kt 
|  |-Pot.yaml 
|-Pot.yaml
```

## Interop between languages

Kotlin Multiplatform implies smooth interop with platform languages, APIs, and frameworks.
There are tree distinct scenarios where such interoperability is needed:

- Consuming: Kotlin code can use APIs from existing platform libraries, e.g. jars on JVM or CocoaPods on iOS.  
- Publishing: Kotlin code can be compiled and published as platform libraries to be consumed by the target platform's tooling; such as jars on JVM, *.so on linux or frameworks on iOS.    
- Joint compilation: Kotlin code be compiled and linked into a final product together with the platform languages, like JVM, C, Objective-C and Swift.

_NOTE: Kotlin JVM supported all these scenarios from the beginning.
However, full interoperability is currently not supported in the Kotlin Native._

Here is how the interop is designed to work in the current DSL design:

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
|-Pot.yaml
```

## Multi-platform configuration

### Platform qualifier

Use the `@platform`-qualifier to mark platform-specific source folders and sections in the Pot.yaml files. 
You can use Kotlin Multiplatform [platform names and families](https://kotlinlang.org/docs/multiplatform-hierarchy.html) as `@platform`-qualifier.
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
|-Pot.yaml
```

Also, you can share code between several platforms by using `aliases:`

```yaml
product:
  type: lib
  platforms: [iosArm64, android, jvm]

aliases:
  jvmAndAndroid: [jvm, android]

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

For [Kotlin Multiplatform expect/actual declarations](https://kotlinlang.org/docs/multiplatform-connect-to-apis.html), put your `expected` declarations into the `src/` folder, and `actual` declarations into the corresponding `src@<platform>/` folders. 

### Multi-platform dependencies

When you use a Kotlin Multiplatform library, its platforms-specific parts are automatically configured for each Pot platform.

Example:
To add a [KmLogging library](https://github.com/LighthouseGames/KmLogging) to a multi-platform Pot, simply write

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
    compileSdkVersion: android-33

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
    languageFeatures: [x]
  ios:              # iOS toolchain
    deploymentTarget: 17

settings@android:   # specialization for Android platform
  compose:          # Compose toolchain
    enabled: true

settings@ios:       # specialization for all iOS platforms
  kotlin:           # Kotlin toolchain
    languageVersion: 1.9
    languageFeatures: [y]

settings@iosArm64:  # specialization for iOS arm64 platform 
  ios:              # iOS toolchain
    deploymentTarget: 18
```
The effective settings are:
```yaml 
settings@android:
  kotlin:
    languageVersion: 1.8   # from settings:
    languageFeatures: [x]  # from settings:
  compose:
   enabled: true           # from settings@android:
```
```yaml 
settings@iosArm64:
  kotlin:
    languageVersion: 1.9      # from settings@ios:
    languageFeatures: [x, y]  # merged from settings: and settings@ios:
  ios:
    deploymentTarget: 18      # from settings@iosArm64:
```
```yaml 
settings@iosSimulatorArm64:
  kotlin:
    languageVersion: 1.9      # from settings@ios:
    languageFeatures: [x, y]  # merged from settings: and settings@ios:
  ios:
    deploymentTarget: 17      # from settings:
```

### Dependency/Settings propagation
Common `dependencies:` and `settings:` are automatically propagated to the platform families and platforms in `@platform`-sections, using the following rules:
- Scalar values (strings,  numbers etc.) are overridden by more specialized `@platform`-sections.
- Mappings and lists are appended.

Think of the rules like adding merging Java/Kotlin Maps.

## Build variants

In the native world it's rather common to have at least two types of build configurations, release and debug.
In the release configuration settings like compiler optimizations and obfuscation are enabled, while in the debug mode they are disabled and additional debug information is generated.

In Android world in addition to the [debug and release build types](https://developer.android.com/build/build-variants#build-types), there exists a concept of [product flavors](https://developer.android.com/build/build-variants#product-flavors). Product flavor is a slight modification of the final product, e.g. with paid features or ads for a free version. Product flavors are also used for [white labeling](https://en.wikipedia.org/wiki/White-label_product), e.g. to add a logo or certain resources to the application without modify the code.

To support such configurations the DSL offers a concept of _build variants_. A build variant can have additional code, resources, override/append dependencies and settings.

_NOTE: build variants are not yet fully implemented._
                       
Here is how a basic build variants configuration look like:
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
|-Pot.yaml 
```

You might have noticed that build variants configuration uses the `@variant`-qualifier, similarly to the [`@platform`-qualifier](#platform-qualifier). Also, the same rule as with [platform-specific sections](#multi-platform-configuration-1) apply to the build variants.
        

#### Advanced build variants

In order to model both Android build types and Android product flavors, multidimensional build variants are supported: 
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
|-Pot.yaml 
```

#### Build variants and Multiplatform

Build variants can be combined with multi-platform configuration as well. The DSL offers a special `@platform+variant`-notation:

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
|-Pot.yaml 
```

## Templates

In modularized projects there is often a need to have a certain common configuration for some or all or some modules. Typical examples could be a testing framework used in all modules or a Kotlin language version.

DSL offers a way to extract whole sections or their parts into reusable template files. These files are named `<name>.Pot-template.yaml` and have same structure as `Pot.yaml` files. Templates could be applied to any Pot.yaml in the `apply:` section.

E.g. Pot.yaml:
```yaml
product: jvm/app

apply: 
  - ../common.Pot-template.yaml
```

../common.Pot-template.yaml:
```yaml
test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.10
  
settings: 
  kotlin:
    languageVersion: 1.8
```

Sections in the template can also have `@platform`-qualifiers. See the [Multi-platform configuration](#multi-platform-configuration) section for details.

_NOTE: Template files can't have `products:` and `apply:` sections. That is, templates can't be recursive. Templates can't define product lists._

Templates are applied one by one, using the same rules as [platform-specific dependencies and settings](#dependencysettings-propagation):
- Scalar values (strings,  numbers etc.) are overridden.
- Mappings and lists are appended.

Settings and dependencies from the Pot.yaml file are applied last. Position of the `apply:` section doesn't matter, o Pot.yaml file always have precedence E.g.

common.Pot-template.yaml:
```yaml
dependencies:
  - ../shared
  
settings: 
  kotlin:
    languageVersion: 1.8
  compose:
    enabled: true
```

Pot.yaml:
```yaml
product: jvm/app

apply: 
  - ./common.Pot-template.yaml

dependencies:
  - ../jvm-util

settings:
  kotlin:
    languageVersion: 1.9
  java:
    target: 1.8
```

After template application the resulting effective Pot is:
Pot.yaml:
```yaml
product: jvm/app

dependencies:  # lists appended
  - ../shared
  - ../jvm-util

settings:  # objects merged
  kotlin:
    languageVersion: 1.9  # Pot.yaml overwrites value
  compose:                # from the template
    enabled: true
  java:
    target: 1.8   # from the Pot.yaml
```

## Extensibility

_NOTE: Extensibility is not yet fully designed or implemented._

The main design goal for DSL is simplicity, and ease of use specifically for Kotlin and Kotlin Multiplatform.
With that in mind, we took an opposite approach to that Gradle follow. 
Gradle is striving to provide a very flexible and extensible [build execution engine](https://melix.github.io/blog/2021/01/the-problem-with-gradle.html).
Or focus, on the other hand, is to provide great user experience of the box. That's why there are many aspects that are available in the DSL as first-class citizens. 


Aspects, such as streamlined [multiplatform](#multi-platform-configuration) setup,
built-in support for [CocoaPods dependencies](#native-dependencies), 
straightforward [Compose Multiplatform configuration](#configuring-compose-multiplatform), etc., 
should  enable easy onboarding and quick start. Nevertheless, as projects grow, Kotlin ecosystem expand, and more use cases emerge,
its inevitable that some level of extensibility will be needed. 

The following aspects are designed to be extensible:
- [Product types](#product-types) - an extension could provide additional product types, such as a Space or a Fleet plugin, an application server app, etc.   
- [Publishing](#publishing) - there might be need to publish to, say, vcpkg or a specific marketplace, which are not supported out of the box.  
- [External Dependency](#native-dependencies) - similarly to publishing, there might be need to consume dependencies from vcpkg or other package managers.
- [Toolchains](#settings) - toolchains are the main actors in the build pipeline extensibility - they provide actual build logic for linting, code generation, compilation, obfuscation.  

Extensions are supposed to contribute to DSL using declarative approach (e.g. via schemes),
and also implement the actual logic with regular imperative language (read, Kotlin). Such a mixed approach should allow for fast project structure evaluation (no configuration phase as in Gradle) and flexibility at the same time. 

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
|-pot-extensions/
|  |-my-source-processor/
|  |  |-pot-extension.yaml      # generated or manually created DSL 
|  |  |-extension.kt            # implementation
|-Pot.yaml 
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

The DSL engine would be able to quickly discover DSL schema for `setting:my-source-processor:` when evaluatig the project structure. And also compiler and execute arbitrary login defined in Kotlin file.     

## Gradle-based projects

The current implementation is Gradle-based. You need a settings.gradle.kts file in the project root with the DSL plugin:
```
|-src/
|  |-...
|-Pot.yaml
|-settings.gradle.kts
```

Or in case or multi-module projects:

```
|-app/
|  |-...
|  |-Pot.yaml
|-lib/
|  |-...
|  |-Pot.yaml
|-settings.gradle.kts
```

settings.gradle.kts:
```kotlin
buildscript {
    // Configured repositories required for the DSL plugin
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }

    // Add the DSL plugin into Gradle's classpath
    dependencies {
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:121-NIGHTLY")
    }
}

// Apply the DSL plugin
plugins.apply("org.jetbrains.deft.proto.settings.plugin")
```

### Gradle interop

Work in progress, documentation is coming soon...

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
