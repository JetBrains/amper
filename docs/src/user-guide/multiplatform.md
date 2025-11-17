# Multiplatform modules

Amper was built from the start with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform/kmp-overview.html)
in mind. Kotlin Multiplatform is a technology that allows building a single module for different target platforms.

## Supported platforms

In the diagram below, you'll find all supported platforms.
Some target platforms belong to the same _family_ and share some common APIs.

The following diagram shows the hierarchy between all platform families (intermediate nodes) and platforms (leaf nodes):

``` title="Defaut platforms hierarchy"
common
 ├─ jvm
 ├─ android
 ├─ web
 │   ├─ js
 │   ╰─ wasmJs
 ├─ wasmWasi
 ╰─ native
     ├─ linux
     │   ├─ linuxX64
     │   ╰─ linuxArm64
     ├─ mingw
     │   ╰─ mingwX64
     ├─ apple
     │   ├─ macos
     │   │   ├─ macosX64
     │   │   ╰─ macosArm64
     │   ├─ ios
     │   │   ├─ iosArm64
     │   │   ├─ iosSimulatorArm64
     │   │   ╰─ iosX64
     │   ├─ watchos
     │   │   ├─ watchosArm32
     │   │   ├─ watchosArm64
     │   │   ├─ watchosDeviceArm64
     │   │   ╰─ watchosSimulatorArm64
     │   ╰─ tvos
     │       ├─ tvosArm64
     │       ├─ tvosSimulatorArm64
     │       ╰─ tvosX64
     ╰─ androidNative
         ├─ androidNativeArm32
         ├─ androidNativeArm64
         ├─ androidNativeX64
         ╰─ androidNativeX86
```

!!! warning "The platforms listed here are not all equally supported or tested."

## Choosing target platforms

Not all multiplatform modules support all target platforms. Most modules define a limited subset of the target
platforms. To do so, use the `product.platforms` list:

```yaml
product:
  type: lib
  platforms: [iosArm64, android, jvm]
```

??? note "No platform family shortcuts here"
    The `product.platforms` list may only contain _platform_ names, not _platform family_ names.
    This is to ensure the stability of the platforms list when Kotlin is bumped to a higher version.
    Using family shortcuts could change the list of platforms in a very subtle and implicit way.

## Platform qualifier

In multiplatform modules, some source directories and sections in the configuration files can be platform-specific.
Amper defines a special suffix, called the `@platform` qualifier, to mark such platform-specific things.

What follows the `@` sign is the name of a _platform_ or _platform family_. The available platform and platform families
are described in the [Platforms hierarchy](#supported-platforms) section.

We'll see in the next sections how these directories and settings interact.

## Module layout

Here is an overview of what the layout of a multiplatform module looks like when `jvm`, `iosArm64`, `iosSimulatorArm64`,
and `iosX64` platforms are enabled:

--8<-- "includes/module-layouts/kmp-lib.md"

!!! note 
    Some other @platform directories were omitted for brevity.

We'll explain what's going on here in the following sections.

### Source dirs

Based on the [platforms hierarchy](#supported-platforms), more common code is visible from more platform-specific code, 
but not vice versa:

```
├─ src/
├─ src@native/            # sees declarations from src
├─ src@apple/             # sees declarations from src + src@native
├─ src@ios/               # sees declarations from src + src@native + src@apple
├─ src@iosArm64/          # sees declarations from src + src@native + src@apple + src@ios
├─ src@iosSimulatorArm64/ # sees declarations from src + src@native + src@apple + src@ios
├─ src@jvm/               # sees declarations from src
╰─ module.yaml
```

You can therefore share code between platforms by placing it in a common ancestor in the hierarchy:
code placed in `src@ios` is shared between `iosArm64` and `iosSimulatorArm64`, for instance.

For [Kotlin Multiplatform expect/actual declarations](https://kotlinlang.org/docs/multiplatform-connect-to-apis.html),
put your `expected` declarations into the `src/` folder, and `actual` declarations into the corresponding
`src@<platform>/` folders.

### Resources

The final artifact for each platform gets its resources from `resources` and all `resources@platform` directories that
correspond to this platform of its parent families:

```
├─ resources/          # these resources are copied into the Android and JVM artifact
├─ resources@android/  # these resources are copied into the Android artifact
╰─ resources@jvm/      # these resources are copied into the JVM artifact
```

If different resource directories contain a resource with the same name, the more common resource is overwritten by the 
more specific ones.
That is `resources/foo.txt` will be overwritten by `resources@android/foo.txt`.

## Aliases

If the [default hierarchy](#supported-platforms) is not enough, you can define new groups of platforms by giving them 
an alias.
You can then use the alias in places where `@platform` suffixes usually appear to share code, settings, or dependencies:

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

# these settings will affect both jvm and android code, and the shared code placed in src@jvmAndAndroid
settings@jvmAndAndroid:
  kotlin:
    freeCompilerArgs: [ -jvm-default=no-compatibility ]
```

```
├─ src/
├─ src@jvmAndAndroid/ # sees declarations from src/
├─ src@jvm/           # sees declarations from src/ and src@jvmAndAndroid/
╰─ src@android/       # sees declarations from src/ and src@jvmAndAndroid/
```

## Multiplatform dependencies

When you use a Kotlin Multiplatform library, its platforms-specific parts are automatically configured for each module platform.

Example:
To add the [KmLogging library](https://github.com/DiamondEdge1/KmLogging) to a multiplatform module, simply write

```yaml
product:
  type: lib
  platforms: [android, iosArm64, jvm]

dependencies:
  - com.diamondedge:logging:2.1.0
```

The effective dependency lists are:

```yaml
dependencies@android:
  - com.diamondedge:logging:2.1.0
  - com.diamondedge:logging-android:2.1.0
```
```yaml
dependencies@iosArm64:
  - com.diamondedge:logging:2.1.0
  - com.diamondedge:logging-iosarm64:2.1.0
```
```yaml
dependencies@jvm:
  - com.diamondedge:logging:2.1.0
  - com.diamondedge:logging-jvm:2.1.0
```

For the explicitly specified dependencies in the `@platform`-sections the general 
[propagation rules](#dependencysettings-propagation) apply. That is, for the given configuration:

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

## Multiplatform settings

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
```

There are situations when you need to override certain settings for a specific platform only.
You can use `@platform`-qualifier.

Note that certain platform names match the toolchain names, e.g. Android:

- `settings@android` qualifier specifies settings for all Android target platforms
- `settings.android` is an Android toolchain settings

This could lead to confusion in cases like:

```yaml
product: android/app

settings@android:    # settings to be used for Android target platform
  android:           # Android toolchain settings
    compileSdk: 33
  kotlin:        # Kotlin toolchain settings
    languageVersion: 1.8
```

Luckily, there should rarely be a need for such a configuration.
We also plan to address this by linting with conversion to a more readable form:

```yaml
product: android/app

settings:
  android:           # Android toolchain settings
    compileSdk: 33
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
  android:              # Android toolchain
    compileSdk: 33

settings@android:   # specialization for Android platform
  compose: enabled  # Compose toolchain

settings@ios:       # specialization for all iOS platforms
  kotlin:           # Kotlin toolchain
    languageVersion: 1.9
    freeCompilerArgs: [y]

settings@iosArm64:  # specialization for iOS arm64 platform 
  ios:              # iOS toolchain
    freeCompilerArgs: [z]
```

The effective settings are:

```yaml 
settings@android:
  kotlin:
    languageVersion: 1.8   # from settings:
    freeCompilerArgs: [x]  # from settings:
  compose: enabled         # from settings@android:
  android:                
    compileSdk: 33         # from settings@android:
```
```yaml 
settings@iosArm64:
  kotlin:
    languageVersion: 1.9      # from settings@ios:
    freeCompilerArgs: [x, y]  # merged from settings: and settings@ios:
```
```yaml 
settings@iosSimulatorArm64:
  kotlin:
    languageVersion: 1.9      # from settings@ios:
    freeCompilerArgs: [x, y, z]  # merged from settings: and settings@ios: and settings@iosArm64:
```

## Dependency/Settings propagation

Common `dependencies:` and `settings:` are automatically propagated to the platform families and platforms in
`@platform`-sections, using the following rules:

- Scalar values (strings, numbers etc.) are overridden by more specialized `@platform`-sections.
- Mappings and lists are appended.

Think of the rules like adding merging Java/Kotlin Maps.

## Interoperability between languages

Kotlin Multiplatform implies smooth interoperability with platform languages, APIs, and frameworks.
There are three distinct scenarios where such interoperability is needed:

- Consuming: Kotlin code can use APIs from existing platform libraries, e.g. jars on JVM (later CocoaPods on iOS too).
- Publishing: Kotlin code can be compiled and published as platform libraries to be consumed by the target platform's
  tooling; such as jars on JVM, frameworks on iOS (maybe later .so on linux).
- Joint compilation: Kotlin code be compiled and linked into a final product together with the platform languages, like
  JVM, Objective-C, and Swift.

Joint compilation is already supported for Java and Kotlin, with 2-way interoperability: Java code can reference Kotlin
declarations, and vice versa.
So Java code can be placed alongside Kotlin code in the same source folder that is compiled for JVM/Android:

```
├─ src/
│  ├─ main.kt
├─ src@jvm/
│  ├─ KotlinCode.kt
│  ├─ JavaCode.java
├─ src@android/
│  ├─ KotlinCode.kt
│  ├─ JavaCode.java
├─ src@ios/
│  ╰─ ...
╰─ module.yaml
```

In the future, Kotlin Native will also support joint Kotlin+Swift compilation in the same way,
but this is not the case yet.
At the moment, Kotlin code is first compiled into a single framework per `ios/app` module,
and then Swift is compiled using the Xcode toolchain with a dependency on that framework.
This means that Swift code can reference Kotlin declarations, but Kotlin cannot reference Swift declarations.
See more in the dedicated [Swift support](builtin-tech/ios.md#swift-support) section.
