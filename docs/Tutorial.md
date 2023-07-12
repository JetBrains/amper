### Step 1. Hello, World

First thing you’d want to try when getting familiar with a new tool is just a simple hello world application. Here is what we do:

Create a `Pot.yaml` file:

```YAML
product: jvm/app
```

And add some code in the `src/` folder:

```
|-src/
|  |-main.kt
|-Pot.yaml
```

`main.kt` file:
```kotlin
fun main() {
    println("Hello, World!")
}
```

That’s it, we’ve just created a simple JVM application.

Oh, and since it’s JVM, let’s also add some Java code.

```
|-src/
|  |-main.kt
|  |-JavaClass.java
|-Pot.yaml
```

As with IntelliJ projects Java and Kotlin can reside together, no need to create separate Maven-like `java/` and `kotlin/` folders.

Examples: [jvm-hello-world](../examples/jvm-hello-world), [jvm-kotlin+java](../examples/jvm-kotlin+java).

### Step 2. Add dependencies

The next thing one usually does is adding dependency on a library:

```YAML
product: jvm/app

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0
```

We’ve just added a dependency on a Kotlin library from the Maven repository. 

Examples: [jvm-with-tests](../examples/jvm-with-tests).

### Step 3. Add tests

Now let’s write some tests. First, we add a test framework:

```YAML
product: jvm/app

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

Examples: [jvm-with-tests](../examples/jvm-with-tests)

### Step 4. Configure Java and Kotlin

Another typical task is configuring compiler settings, such as language level etc. Here is how we do it:

```YAML
product: jvm/app

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

One of the primary target use cases is the Kotlin Multiplatform project (see [Mercury](https://jetbrains.team/blog/Introducing_Project_Mercury) project). 

Let’s see how we can configure multi-platform library for iOS and Android platforms. (The files, dependencies and settings are for DSL demonstration, they’ll probably be different for real projects)

Pot.yaml:

```YAML
product:
  type: lib
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

settings@ios:
  kotlin:
    debug: true
```

And the file layout:

```
|-src/
|  |-Util.kt
|-test/
|  |-MyTest.kt
|-src@android/
|  |-AndroidUtil.kt
|-src@ios/
|  |-iOSUtil.kt
|-Pot.yaml
```

One thing you might have noticed is the `@platform` suffixes. They are platform qualifiers which instruct the build tool to only use the corresponding declaration when building for the corresponding platform. `@platform` qualifier can be applied to source folders, `dependencies:` and `settings:`.

Another interesting thing is `pod: 'Alamofire'` dependency. This is a CocoaPods dependency, a popular package manager for macOS and iOS. It’s an example of a native dependencies, which are declared using a syntax specific for each dependency type.

### Step 6. Modularize

Let's add a couple of application modules that use our multi-platform library:

/shared/Pot.yaml:
```YAML
product:
  type: lib
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
```

/android/Pot.yaml:
```YAML
product: android/app

dependencies:
  - ../shared

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  kotlin:
    languageVersion: 1.8
  android:
    manifestFile: AndroidManifest.xml
```

/ios/Pot.yaml:

```YAML
product: ios/app

dependencies:
  - ../shared

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  kotlin:
    languageVersion: 1.8
  ios:
    sceneDelegateClass: AppDelegate
```

File layout:

```
|-shared/
|  |-src/
|  |  |-Util.kt
|  |-test/
|  |  |-MyTest.kt
|  |-src@android/
|  |  |-AndroidUtil.kt
|  |-src@ios/
|  |  |-iOSUtil.kt
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

### Step 7. Deduplicate common parts

You might have noticed that there are some common settings present in all `Pot.yaml` files. We now can extract the into a template.

Let's create a `<name>.Pot-template.yaml` file:

/common.Pot-template.yaml:
```YAML
test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  kotlin:
    languageVersion: 1.8
```

And apply it to our Pot.yaml files:

/shared/Pot.yaml:
```YAML
product:
  type: lib
  platforms: [android, iosArm64]

apply:
  - ../common.Pot-template.yaml

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0

dependencies@ios:
  - pod: 'Alamofire'
    version: '~> 2.0.1'
```

/android/Pot.yaml:
```YAML
product: android/app

apply:
  - ../common.Pot-template.yaml

dependencies:
  - ../shared

settings:
  android:
    manifestFile: AndroidManifest.xml
```

/ios/Pot.yaml:

```YAML
product: ios/app

apply:
  - ../common.Pot-template.yaml

dependencies:
  - ../shared

settings:
  ios:
    sceneDelegateClass: AppDelegate
```


File layout:
```
|-shared/
|  |-...
|  |-Pot.yaml
|-android/
|  |-...
|  |-Pot.yaml
|-ios/
|  |-...
|  |-Pot.yaml
|-common.Pot-template.yaml
```

Now we can place all common dependencies and settings into the template. Or have multiple templates for various typical configurations in our codebase.

Examples: [templates](../examples/templates)