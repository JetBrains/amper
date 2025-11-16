# Kotlin Multiplatform projects

## Platform qualifier

Use the `@platform`-qualifier to mark platform-specific source folders and sections in the `module.yaml` files.
You can use Kotlin Multiplatform [platform names](https://kotlinlang.org/docs/native-target-support.html) and families 
as `@platform`-qualifier.

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
├─ src/                      # common code for all platforms
├─ src@ios/                  # sees declarations from src/
╰─ src@iosArm64/             # sees declarations from src/ and from src@ios/
```
```
├─ resources/                # resources for all platforms
├─ resources@ios/
╰─ resources@iosArm64/
```

See also how the [resources](basics.md#resources) are handled in the multiplatform projects.


Only the platform names (but not the platform family names) can be currently used in the `platforms:` list:

```yaml
product:
  type: lib
  platforms: [iosArm64, android, jvm]
```

## Platforms hierarchy

In the diagram below, you'll find all supported platforms.
Some target platforms belong to the same family and share some common APIs.
They form a hierarchy as follows:

???+ info "Platform hierarchy diagram"

    ```mermaid
    %%{ init: { 'flowchart': { 'htmlLabels': 'false' } } }%%
    flowchart LR
      %% Intermediate nodes use rounded corners; leaves use stadium shape
    
      common[**common**]
    
      common --> jvm([jvm])
      common --> android([android])
    
      common --> web[**web**]
      web --> js([js])
      web --> wasmJs([wasmJs])
    
      common --> wasmWasi([wasmWasi])
    
      common --> native[**native**]
    
      native --> linux[**linux**]
      linux --> linuxX64([linuxX64])
      linux --> linuxArm64([linuxArm64])
    
      native --> mingw[**mingw**]
      mingw --> mingwX64([mingwX64])
    
      native --> apple[**apple**]
    
      apple --> macos[**macos**]
      macos --> macosX64([macosX64])
      macos --> macosArm64([macosArm64])
    
      apple --> ios[**ios**]
      ios --> iosArm64([iosArm64])
      ios --> iosSimulatorArm64([iosSimulatorArm64])
      ios --> iosX64([iosX64])
    
      apple --> watchos[**watchos**]
      watchos --> watchosArm32([watchosArm32])
      watchos --> watchosArm64([watchosArm64])
      watchos --> watchosDeviceArm64([watchosDeviceArm64])
      watchos --> watchosSimulatorArm64([watchosSimulatorArm64])
    
      apple --> tvos[**tvos**]
      tvos --> tvosArm64([tvosArm64])
      tvos --> tvosSimulatorArm64([tvosSimulatorArm64])
      tvos --> tvosX64([tvosX64])
    
      native --> androidNative[**androidNative**]
      androidNative --> androidNativeArm32([androidNativeArm32])
      androidNative --> androidNativeArm64([androidNativeArm64])
      androidNative --> androidNativeX64([androidNativeX64])
      androidNative --> androidNativeX86([androidNativeX86])
    ```
    
!!! warning "The platforms listed here are not all equally supported or tested."

Based on this hierarchy, common code is visible from more `@platform`-specific code, but not vice versa:

```
├─ src/
│  ╰─ ...
├─ src@ios/                  # sees declarations from src/
│  ╰─ ...
├─ src@iosArm64/             # sees declarations from src/ and from src@ios/
│  ╰─ ...
├─ src@iosSimulatorArm64/    # sees declarations from src/ and from src@ios/
│  ╰─ ...
├─ src@jvm/                  # sees declarations from src/
│  ╰─ ...
╰─ module.yaml
```

You can therefore share code between platforms by placing it in a common ancestor in the hierarchy:
code placed in `src@ios` is shared between `iosArm64` and `iosSimulatorArm64`, for instance.

For [Kotlin Multiplatform expect/actual declarations](https://kotlinlang.org/docs/multiplatform-connect-to-apis.html), 
put your `expected` declarations into the `src/` folder, and `actual` declarations into the corresponding 
`src@<platform>/` folders.

This hierarchy applies to `@platform`-qualified sections in the configuration files as well.
We'll see how this works more precisely in the [Multiplatform Dependencies](#multiplatform-dependencies) and
[Multiplatform Settings](#multiplatform-settings) sections.

### Aliases

If the default hierarchy is not enough, you can define new groups of platforms by giving them an alias.
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
To add the [KmLogging library](https://github.com/LighthouseGames/KmLogging) to a multiplatform module, simply write

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
