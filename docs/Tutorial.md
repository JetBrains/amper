This tutorial gives a short introduction to the DSL and how to use it a new project.

If you want to follow the tutorial:
* Check the [setup instructions](Setup.md)
* Open [a new project template](../examples/new-project-template) in the IDE  

If you are looking to more detailed info, check [the documentation](Documentation.md).
 
_NOTE: Since the current implementation is Gradle-based, every project needs a settings.gradle.kts. 
New templates and example projects are preconfigured. To manually configure your project, see [documentation on Gradle-based projects](Documentation.md#gradle-based-projects)._

### Step 0. Prepare

* JDK 17+ is required. Make sure you have it installed. 

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

And since it’s JVM, let’s also add some Java code.

```
|-src/
|  |-main.kt
|  |-JavaClass.java
|-Pot.yaml
```

As with IntelliJ projects Java and Kotlin can reside together, no need to create separate Maven-like `java/` and `kotlin/` folders.

_NOTE: In a [Gradle-based project](Documentation.md#gradle-based-projects) the settings.gradle.kts should be located in the project root:_
```
|-src/
|  |-...
|-Pot.yaml
|-settings.gradle.kts
```

Examples: [jvm-hello-world](../examples/jvm-hello-world), [jvm-kotlin+java](../examples/jvm-kotlin+java).

Documentation:
- [Project layout](Documentation.md#project-layout)
- [Manifest file anatomy](Documentation.md#pot-manifest-file-anatomy)

### Step 2. Add dependencies

The next thing one usually does is adding dependency on a library:

```YAML
product: jvm/app

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0
```

We’ve just added a dependency on a Kotlin library from the Maven repository. 

Examples: [jvm-with-tests](../examples/jvm-with-tests).

Documentation:
- [Dependencies](Documentation.md#dependencies)

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

Documentation:
- [Tests](Documentation.md#tests)

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

Documentation:
- [Settings](Documentation.md#settings)

### Step 5. Add Compose Multiplatform

To use Compose Multiplatform framework, add corresponding dependencies and a Compose toolchain section in `settings:`.

/android/Pot.yaml:
```YAML
product: jvm/app

dependencies:
  # add Compose dependencies
  - org.jetbrains.compose.desktop:desktop-jvm:1.4.1

settings:
  # enable Compose toolchain
  compose:
    enabled: true
```

Examples: [compose-desktop](../examples/compose-desktop), [compose-android](../examples/compose-android)

Documentation:
- [Configuring Compose Multiplatform](Documentation.md#configuring-compose-multiplatform)

### Step 6. Modularize

Let's split our app into a library and an application modules:

/app/Pot.yaml:
```YAML
product: jvm/app

dependencies:
  - ../shared
  - org.jetbrains.compose.desktop:desktop-jvm:1.4.1

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  compose:
    enabled: true
```

/shared/Pot.yaml:
```YAML
product:
  type: lib
  platforms: [jvm]

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0
```

File layout:
```
|-app/
|  |-src/
|  |  |-main.kt
|  |-Pot.yaml
|-shared/
|  |-src/
|  |  |-Util.kt
|  |-test/
|  |  |-UtilTest.kt
|  |-Pot.yaml
```

In this example, the internal dependencies on the `shared` pot are declared using relative paths. No need to give additional names to the libraries.

_NOTE: In a [Gradle-based project](Documentation.md#gradle-based-projects) the settings.gradle.kts should be located in the project root:_
```
|-app/
|  |-...
|  |-Pot.yaml
|-shared/
|  |-...
|  |-Pot.yaml
|-settings.gradle.kts
```

Examples: [modularized](.././examples/modularized).

Documentation:
- [Internal dependencies](Documentation.md#internal-dependencies)

### Step 7. Make project multi-platform

One of the primary target use cases is the Kotlin Multiplatform (see the [Mercury project](https://jetbrains.team/blog/Introducing_Project_Mercury)). 
So let’s add client Android and iOS apps our project. 

Pot.yaml for Android:
```YAML
product: android/app

dependencies:
  - ../shared
  - org.jetbrains.compose.foundation:foundation:1.4.1
  - org.jetbrains.compose.material:material:1.4.1

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  compose:
    enabled: true
```

Pot.yaml for iOS:

```YAML
product: ios/app

dependencies:
  - ../shared
  - org.jetbrains.compose.foundation:foundation:1.4.1
  - org.jetbrains.compose.material:material:1.4.1

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  compose:
    enabled: true
```

And update the shared module:
_NOTE: Currently lib modules require an explicit list of platform. We plan to automatically configure library modules with required platforms in the future_   

/shared/Pot.yaml:
```YAML
product:
  type: lib
  platforms: [jvm, android, iosArm64, iosSimulatorArm64]

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0
```

The new file layout:
```
|-android-app/
|  |-src/
|  |  |-main.kt
|  |  |-AndroidManifest.xml
|  |-Pot.yaml
|-ios-app/
|  |-src/
|  |  |-main.kt
|  |-Pot.yaml
|-jvm-app/
|  |-src/
|  |  |-main.kt
|  |-Pot.yaml
|-shared/
|  |-src/
|  |  |-Util.kt
|  |-test/
|  |  |-UtilTest.kt
|  |-Pot.yaml
```

Now we have a `shared` module, which is used by client apps on different platforms. 
So we might need to add some platform-specific code, like [Kotlin Multiplatform expect/actual declarations](https://kotlinlang.org/docs/multiplatform-connect-to-apis.html) and corresponding dependencies.

Let's add some platform-specific networking code and dependencies.

_NOTE: Native dependencies (like CocoaPods) are currently not implemented._

/shared/Pot.yaml:
```YAML
product:
  type: lib
  platforms: [jvm, android, iosArm64, iosSimulatorArm64, iosX64]

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0
    
dependenceis@ios:
  - pod: 'Alamofire'
    version: '~> 2.0.1'

dependenceis@android:
  - com.squareup.retrofit2:retrofit:2.9.0

dependenceis@jvm:
  - com.squareup.okhttp3:mockwebserver:4.10.0

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0
```

Possible file layout:
```
|-android-app/
|  |-...
|-ios-app/
|  |-...
|-jvm-app/
|  |-...
|-shared/
|  |-src/
|  |  |-Util.kt
|  |  |-networking.kt
|  |-src@android/
|  |  |-networking.kt
|  |-src@jvm/
|  |  |-networking.kt
|  |-src@ios/
|  |  |-networking.kt
|  |-test/
|  |  |-UtilTest.kt
|  |-Pot.yaml
```

One thing you might have noticed is the `@platform` suffixes. 
They are platform qualifiers which instruct the build tool to only use the corresponding declaration when building for the corresponding platform.
`@platform` qualifier can be applied to source folders, `dependencies:` and `settings:`.

Another interesting thing is `- pod: 'Alamofire'` dependency. This is a CocoaPods dependency, a popular package manager for macOS and iOS.
It’s an example of a native dependencies, which are declared using a syntax specific for each dependency type.

Examples: [multiplatform](../examples/multiplatform)

Documentation:
- [Multi-platform configuration](Documentation.md#multi-platform-configuration)


### Step 8. Deduplicate common parts

You might have noticed that there are some common dependencies and settings present in `Pot.yaml` files. We now can extract the into a template.

Let's create a couple of `<name>.Pot-template.yaml` files:

/common.Pot-template.yaml:
```YAML
test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  kotlin:
    languageVersion: 1.8
```

/app.Pot-template.yaml:
```YAML
dependencies:
  - ./shared
  
settings:
  compose:
    enabled: true
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
    
dependenceis@ios:
  - pod: 'Alamofire'
    version: '~> 2.0.1'

dependenceis@android:
  - com.squareup.retrofit2:retrofit:2.9.0

dependenceis@jvm:
  - com.squareup.okhttp3:mockwebserver:4.10.0
```

/android-app/Pot.yaml:
```YAML
product: android/app

apply:
  - ../common.Pot-template.yaml
  - ../app.Pot-template.yaml

dependencies:
  - org.jetbrains.compose.foundation:foundation:1.4.1
  - org.jetbrains.compose.material:material:1.4.1
```

/ios-app/Pot.yaml:
```YAML
product: ios/app

apply:
  - ../common.Pot-template.yaml
  - ../app.Pot-template.yaml

dependencies:
  - org.jetbrains.compose.foundation:foundation:1.4.1
  - org.jetbrains.compose.material:material:1.4.1
```

/jvm-app/Pot.yaml:
```YAML
product: ios/app

apply:
  - ../common.Pot-template.yaml
  - ../app.Pot-template.yaml

dependencies:
  - org.jetbrains.compose.desktop:desktop-jvm:1.4.1
```

File layout:
```
|-android-app/
|  |-...
|  |-Pot.yaml
|-ios-app/
|  |-...
|  |-Pot.yaml
|-jvm-app/
|  |-...
|  |-Pot.yaml
|-shared/
|  |-...
|  |-Pot.yaml
|-common.Pot-template.yaml
|-app.Pot-template.yaml
```

Now we can place all common dependencies and settings into the template. Or have multiple templates for various typical configurations in our codebase.

Examples: [templates](../examples/templates)

Documentation:
- [Templates](Documentation.md#templates)

### Further steps

Check the [documentation](Documentation.md) and explore [examples](../examples) for more information.