### Basics

**Pot** - a unique set of source files, resources with a `Pot.yaml` manifest file. Pot is a module that describes a single product (which can have several versions foe different platforms).
We don't use the term `module` to avoid confusing them with other modules (Kotlin module, IntelliJ module etc).

The DSL supports Kotlin Multiplatform as a core concept, and offers a special syntax to deal with multi-platform configuration.
There is a dedicated `@platform`-qualifier in order to mark platform-specific code, dependencies, settings etc. You'll see it in the examples below. 

### Project layout

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

In a  JVM Pot you can mix Kotlin and Java code:
```
|-src/             
|  |-main.kt      
|  |-Util.java      
|-Pot.yaml
```

In a Multiplatform Pot put platform-specific implementations in the `@platform`-qualifier folder:
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
In the future we plan to also support a 'flat' multiplatform layout like the one below.
It requires some investment in the IntelliJ platform, so we haven't yet done it. 
```
|-src/             # common and platform-specisic code
|  |-main.kt      
|  |-util.kt       #  API with ‘expect’ part
|  |-util@ios.kt   #  API implementation with ‘actual’ part for iOS
|  |-util@jvm.kt   #  API implementation with ‘actual’ part for JVM
|-Pot.yaml
```

### Manifest file anatomy

`Pot.yaml` is a Pot manifest file and is declared using YAML (here is a [brief intro YAML](#brief-yaml-reference)).  
A `Pot.yaml` file has several main sections: `product:`, `dependencies:` and `settings:` 
- Product type could be either an `app` or a `lib`. A Pot with an `app` type is packaged as executable while a `lib` Pot could be reused from other Pots.
- Pot could be a single platform application or a multi-platform library.

Here is an example of a JVM console application with a single dependency and a specific Kotlin language version:
```yaml
product:
  type: app
  platform: [jvm]

dependencies:
  - io.ktor:ktor-client-core:2.3.0

settings:
  kotlin:
    languageVersion: 1.9
```

#### Multiplatform configuration

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
See [details on multiplatform configuration](#multi-platform-configuration) for more information.

### Dependencies

#### External Maven dependencies

For Maven dependencies simply specify their coordinates:
```yaml
dependencies:
  - org.jetbrains.kotlin:kotlin-serialization:1.8.0
  - io.ktor:ktor-client-core:2.2.0
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

There are three scopes:
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
 - io.ktor:ktor-client-core:2.2.0: [compile-only]  
 - ../ui/utils: [runtime-only]
```

All dependencies by default are not transitive, that is, they are implementation details. 
In order to make a dependency visible to a dependent Pot, you need to explicitly mark it as `transitive` (aka API-dependency). 

```yaml
dependencies:
 - io.ktor:ktor-client-core:2.2.0:
     transitive: true 
 - ../ui/utils:
     transitive: true 
```
There is also an inline form: 
```yaml
dependencies:
 - io.ktor:ktor-client-core:2.2.0: [transitive]  
 - ../ui/utils: [transitive]
```

Here is an example of a `compile-only` and `transitive` dependency:
```yaml
dependencies:
 - io.ktor:ktor-client-core:2.2.0:
     scope: compile-only
     transitive: true
```
Same in the inline form:
```yaml
dependencies:
 - io.ktor:ktor-client-core:2.2.0: [compile-only, transitive]
```

#### Native dependencies (Not yet supported in the prorotype)

To depend on an npm, CocoaPods, or a Swift package, the DSL offers a format, familiar to the users of CocoaPods and SPM:

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

### Settings

All toolchain settings are specified in the dedicated groups of the `settings:` section:
```yaml
settings: 
  kotlin:
    languageVersion: 1.8
    features: [contextReceivers]
  android:
    androidApiVersion: android-31
```

Currently, the following toolchains are supported: `kotlin:`, `java:`, `ios:`, `android:`
See [multiplatform settings configuration](#multiplatform-settings) for more details

### Tests

Test code is located in the `test/` and `test@platform/` folders. Test settings and dependencies by default are inherited from the main configuration according to the [configuration propagation rules](#dependencysettings-propagation). 
To add specific test dependencies put them into `test-dependencies:` section. To add or override toolchain settings in tests, use `test-settings:` section.

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

dependencies:
  - io.ktor:ktor-client-core:2.2.0

# add dependencies for test code
test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.10
  
settings: 
  kotlin:
    languageVersion: 1.8

# set language level for test code
test-settings:
  kotlin:
    languageVersion: 1.9
```

### Multi-platform configuration

#### Platform qualifier

Use `@platform`-qualifier to mark platform-specific sections in Pot.yaml files and source folders. 
You can use Kotlin Multiplatform [platform names and families](https://kotlinlang.org/docs/multiplatform-hierarchy.html) as `@platform`-qualifier when naming source folders, and in `dependencies:` and `settings:` sections.
```yaml
dependencies@ios:               # ios is a platform family name  
dependencies@iosArm64:          # iosArm64 is a KMP platform name
```
```
|-src/             
|-src@ios/                  # sees declarations from src/ 
|-src@iosArm64/             # sees declarations from src/ and from src@ios/ 
```

Only platform names (but not the platform family names) can be currently used in the `platforms:` list:

```yaml
product:
  type: lib
  platforms: [iosArm64, android, jvm]
```


Here is the partial list of platform and families:
```yaml
  jvm  
  linux
    linuxX64
    linuxArm64
  mingw
    mingwX64  
  android  
  apple
    macos
      macosX64
      macosArm64
    ios
      iosArm64
      isSimulatorArm64
      isX64            # iOS Simulator for Intel Mac
  ...
```
Also, you can share code between several platforms by using `aliases:`

```yaml
product:
  type: lib
  platforms: [iosArm64, android, jvm]

aliases:
  jvmAndAndroid: [jvm, android]

dependencies@jvmAndAndroid:
  - org.lighthousegames:logging:1.3.0
```

```
|-src/             
|-src@jvmAndAndroid/ # sees declarations from src/ 
|-src@jvm/           # sees declarations from src/ and src@jvmAndAndroid/              
|-src@android/       # sees declarations from src/ and src@jvmAndAndroid/             
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

For [Kotlin Multiplatform expect/actual declarations](https://kotlinlang.org/docs/multiplatform-connect-to-apis.html), put your `expected` declarations into the `src/` folder, and `actual` declarations into the corresponding `src@<platform>/` folders. 

#### Multiplatform dependencies

When you use a Kotlin multiplatform library, its platforms-specific parts are automatically configured for each Pot platform.

Example:
To add a [KmLogging library](https://github.com/LighthouseGames/KmLogging) to a multiplatform Pot, simply write

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
#### Multiplatform settings

To configure settings for different platforms, you can put them into the `settings:` section.
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

There are situations, when you need to override certain settings in for a specific platform only. 
You can use `@platform`-qualifier. So such settings in the `@platform`-sections the [propagation rules](#dependencysettings-propagation) apply. E.g., for the given configuration:
```yaml
product:
  type: lib
  platforms: [android, iosArm64, iosSimulatorArm64]

settings:
  kotlin:
    languageVersion: 1.8
    features: [x]
    
settings@ios:
  kotlin:
    languageVersion: 1.9
    features: [y]
  ios:
    deploymentTarget: 17

settings@iosArm64:
  ios:
    deploymentTarget: 18
```
The effective settings are:
```yaml 
settings@android:
  kotlin:
    languageVersion: 1.8 # from settings:
    features: [x]       # from settings:
```
```yaml 
settings@iosArm64:
  kotlin:
    languageVersion: 1.9 # from settings@ios:
    features: [x, y]     # merged from settings: and settings@ios:
  ios:
    deploymentTarget: 18 # from settings@iosArm64:
```
```yaml 
settings@iosSimulatorArm64:
  kotlin:
    languageVersion: 1.9 # from settings@ios:
    features: [x, y]     # merged from settings: and settings@ios:
  ios:
    deploymentTarget: 17 # from settings@ios: 
```
#### Dependency/Settings propagation
Common `dependencies:` and `settings:` are automatically propagated to the platform families and platforms, using the following rules:
- Scalar values (strings,  numbers etc.) are overridden by more specialized `@platform`-sections.
- Mappings and lists are appended.
(think of them like adding merging Java/Kotlin Maps).

### Brief YAML reference
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
