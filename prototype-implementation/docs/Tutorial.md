### Step 1. Hello, World

First thing you’d want to try when getting familiar with a new tool is just a simple hello world application. Here is what we do:

Create a `Pot.yaml` file:

```YAML
product:
  type: app
  platform: [jvm]
```

And add some code in the src/ folder:

```
|-src/
|  |-main.kt
|-Pot.yaml
```

That’s it, we’ve just created a simple JVM application.

Oh, and since it’s JVM, let’s also add some Java code.

```
|-src/
|  |-main.kt
|  |-Util.java
|-Pot.yaml
```

As with IntelliJ projects Java and Kotlin can reside together, no need to create separate Maven-like `java/` and `kotlin/` folders.

Examples: [jvm-hello-world](../../examples/jvm-hello-world), [jvm-kotlin+java](../../examples/jvm-kotlin+java).

### Step 2. Add dependencies

The next thing one usually does is adding dependency on a library:

```YAML
product:
  type: app
  platform: [jvm]

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0
```

We’ve just added a dependency on a Kotlin library from the Maven repository. 

Examples: [jvm-with-tests](../../examples/jvm-with-tests).

### Step 3. Add tests

Now let’s write some tests. First, we add a test framework:

```YAML
product:
  type: app
  platform: [jvm]

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0
```

Then, the test code:

```
|-src/
|  |-...
|-test/
|  |-MyTest.kt
|-Pot.yaml
```

Notice that test dependencies are configured as a separate list. It should be very familiar to the Cargo, Flutter and Poetry users.

Examples: [jvm-with-tests](../../examples/jvm-with-tests)

### Step 4. Configure Java and Kotlin

Another typical task is configuring compiler settings, such as language level etc. Here is how we do it:

```YAML
product:
  type: app
  platforms: [jvm]

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  kotlin:
    languageVersion: 1.8
    jvmTarget: 17
  java:
    source: 17
    target: 17
```

### Step 5. Make project multi-platform

One of the target use cases of Deft is the Kotlin Multiplatform project (see [Mercury](https://jetbrains.team/blog/Introducing_Project_Mercury) project). 

Let’s see how we can configure a mobile app for iOS and Android platforms. The files, dependencies and settings are for DSL demonstration, they’ll probably be different for real projects.

Pot.yaml:

```YAML
product:
  type: app
  platforms: [android, iosArm64]

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0

dependencies@ios:
  - pod: 'Alamofire'
    version: '~> 2.0.1'

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  kotlin:
    languageVersion: 1.8

settings@android:
  manifestFile: AndroidManifest.xml
```

And the file layout:

```
|-src/
|  |-Util.kt
|-test/
|  |-MyTest.kt
|-src@android/
|  |-MainActivity.kt
|-src@ios/
|  |-AppDelegate.kt
|-resources@android/
|  |-AndroidManifest.xml
|-Pot.yaml
```

One thing you might have noticed is the `@platform` suffixes. They are platform qualifiers which instruct the build tool to only use the corresponding declaration when building for the corresponding platform. `@platform` qualifier can be applied to source folders, `dependencies:` and `settings:`.

Another interesting thing is `pod: 'Alamofire'` dependency. This is a CocoaPods dependency, a popular package manager for macOS and iOS. It’s an example of a native dependencies, which are declared using a syntax specific for each dependency type.

Examples: [kmp-mobile](../../examples/kmp-mobile) project.

### Step 6. Modularize

As the project grows, we might want to split it into separate modules. Let’s do it.

/shared/Pot.yaml:

```YAML
product:
  type: lib
  platforms: [android, iosArm64]

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  kotlin:
    languageVersion: 1.8
```

/android/Pot.yaml:

```YAML
product:
  type: app
  platforms: [android]

dependencies:
  - ../shared

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  manifestFile: AndroidManifest.xml
  kotlin:
    languageVersion: 1.8
```

/ios/Pot.yaml:

```YAML
product:
  type: app
  platforms: [iosArm64]

dependencies:
  - ../shared

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  kotlin:
    languageVersion: 1.8
```

File layout:

```
|-shared/
|  |-src/
|  |  |-Util.kt
|  |-test/
|  |  |-MyTest.kt
|  |-Pot.yaml
|-android/
|  |-src/
|  |  |-MainActivity.kt
|  |-resources/
|  |  |-AndroidManifest.xml
|  |-Pot.yaml
|-ios/
|  |-src/
|  |  |-AppDelegate.kt
|  |-Pot.yaml
```

In this example, the internal dependencies on the `shared` pot are declared using relative paths. No need to give additional names to the libraries.

Examples: [kmp-mobile-modularized](.././examples/kmp-mobile-modularized).

### Step 7. Configure for debug and release

One of the popular scenarios in the native and mobile world is configuring settings for Debug and Release builds. In addition to that, Android tooling offers so-called [product flavors](https://developer.android.com/studio/build/build-variants).

For these purposes we can use `variants` in the Deft DSL:

```YAML
product:
  type: lib
  platforms: [android, iosArm64]

variants: [debug, release]

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0

dependencies@debug:
  - org.company:kotlin-debug-util:1.0

settings:
  kotlin:
    languageVersion: 1.8

settings@debug:
  kotlin:
    enableDebug: true
```

Files:

```
|-src/
|  |-Util.kt
|-src@debug/
|  |-DebugImpl.kt
|-src@release/
|  |-ReleaseImpl.kt
|-Pot.yaml
```

The `@variant` qualifier works the same way as the `@platform` qualifier. And we could even combine them. E.g. to provide dependency for debug variant when building for android, we could write:

```YAML
dependencies@android+debug:
  - org.company:kotlin-debug-util:1.0
```

Examples: [variants](../../examples/build-variants)