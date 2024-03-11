This tutorial gives a short introduction to Amper and how to use it a new project.

If you are looking to more detailed info, check [the documentation](Documentation.md).

### Before you start
Check the [setup instructions](Setup.md), and open [a new project template](../examples/new-project-template) in the IDE to make sure everything works.

Note, that:
* JDK 17+ is required. Make sure you have it installed. 

### Step 1. Hello, World

First thing you’d want to try when getting familiar with a new tool is just a simple hello world application. Here is what we do:

Create a `module.yaml` file:

```YAML
product: jvm/app
```

And add some code in the `src/` folder:

```
|-src/
|  |-main.kt
|-module.yaml
```

`main.kt` file:
```kotlin
fun main() {
    println("Hello, World!")
}
```

_NOTE: Since Amper is currently [Gradle-based](Documentation.md#gradle-based-projects), a settings.gradle.kts should be located in the project root._
_Copy the [settings.gradle.kts](../examples/new-project-template/settings.gradle.kts) and the [gradle folder](../examples/new-project-template/gradle) from a template project:_
```
|-gradle/...
|-src/
|  |-...
|-module.yaml
|-settings.gradle.kts
```

That’s it, we’ve just created a simple JVM application.

And since it’s JVM, let’s also add some Java code.

```
|-src/
|  |-main.kt
|  |-JavaClass.java
|-module.yaml
```

As with IntelliJ projects Java and Kotlin can reside together, no need to create separate Maven-like `java/` and `kotlin/` folders.

Examples: [jvm-hello-world](../examples/jvm-hello-world), [jvm-kotlin+java](../examples/jvm-kotlin+java).

Documentation:
- [Project layout](Documentation.md#project-layout)
- [Manifest file anatomy](Documentation.md#module-manifest-file-anatomy)

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
|-module.yaml
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
  jvm:
    release: 17
```

Documentation:
- [Settings](Documentation.md#settings)

### Step 5. Add Compose Multiplatform

To use Compose Multiplatform framework, add corresponding dependencies and a Compose toolchain section in `settings:`.

/android/module.yaml:
```YAML
product: jvm/app

dependencies:
  # add Compose dependencies
  - $compose.desktop.currentOs

settings:
  # enable Compose toolchain
  compose: enabled
```

Examples: [compose-desktop](../examples/compose-desktop), [compose-android](../examples/compose-android)

Documentation:
- [Configuring Compose Multiplatform](Documentation.md#configuring-compose-multiplatform)

### Step 6. Modularize

Let's split our app into a library and an application modules:

/app/module.yaml:
```YAML
product: jvm/app

dependencies:
  - ../shared
  - $compose.desktop.currentOs

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  compose: enabled
```

/shared/module.yaml:
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
|  |-module.yaml
|-shared/
|  |-src/
|  |  |-Util.kt
|  |-test/
|  |  |-UtilTest.kt
|  |-module.yaml
```

In this example, the internal dependencies on the `shared` module are declared using relative paths. No need to give additional names to the libraries.

_NOTE: Since Amper is currently [Gradle-based](Documentation.md#gradle-based-projects), a settings.gradle.kts should be located in the project root._
_Copy the [settings.gradle.kts](../examples/new-project-template/settings.gradle.kts) and the [gradle folder](../examples/new-project-template/gradle) from a template project:_
```
|-gradle/...
|-app/
|  |-...
|  |-module.yaml
|-shared/
|  |-...
|  |-module.yaml
|-settings.gradle.kts
```

Examples: [modularized](.././examples/modularized).

Documentation:
- [Internal dependencies](Documentation.md#internal-dependencies)

### Step 7. Make project multi-platform

Let’s add client Android and iOS apps our project. 

module.yaml for Android:
```YAML
product: android/app

dependencies:
  - ../shared
  - $compose.foundation
  - $compose.material3

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  compose: enabled
```

module.yaml for iOS:

```YAML
product: ios/app

dependencies:
  - ../shared
  - $compose.foundation
  - $compose.material3

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  compose: enabled
```

And update the shared module:
_NOTE: Currently lib modules require an explicit list of platform. We plan to automatically configure library modules with required platforms in the future_   

/shared/module.yaml:
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
|  |-module.yaml
|-ios-app/
|  |-src/
|  |  |-main.kt
|  |-module.yaml
|-jvm-app/
|  |-src/
|  |  |-main.kt
|  |-module.yaml
|-shared/
|  |-src/
|  |  |-Util.kt
|  |-test/
|  |  |-UtilTest.kt
|  |-module.yaml
```

Now we have a `shared` module, which is used by client apps on different platforms. 
So we might need to add some platform-specific code, like [Kotlin Multiplatform expect/actual declarations](https://kotlinlang.org/docs/multiplatform-connect-to-apis.html) and corresponding dependencies.

Let's add some platform-specific networking code and dependencies.

_NOTE: Native dependencies (like CocoaPods) are currently not implemented._

/shared/module.yaml:
```YAML
product:
  type: lib
  platforms: [jvm, android, iosArm64, iosSimulatorArm64, iosX64]

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0
    
dependencies@ios:
  - pod: 'Alamofire'
    version: '~> 2.0.1'

dependencies@android:
  - com.squareup.retrofit2:retrofit:2.9.0

dependencies@jvm:
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
|  |-module.yaml
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

You might have noticed that there are some common dependencies and settings present in `module.yaml` files. We now can extract them into a template.

Let's create a couple of `<name>.module-template.yaml` files:

/common.module-template.yaml:
```YAML
test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.0

settings:
  kotlin:
    languageVersion: 1.8
```

/app.module-template.yaml:
```YAML
dependencies:
  - ./shared
  
settings:
  compose: enabled
```

And apply it to our module.yaml files:

/shared/module.yaml:
```YAML
product:
  type: lib
  platforms: [android, iosArm64]

apply:
  - ../common.module-template.yaml

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0
    
dependencies@ios:
  - pod: 'Alamofire'
    version: '~> 2.0.1'

dependencies@android:
  - com.squareup.retrofit2:retrofit:2.9.0

dependencies@jvm:
  - com.squareup.okhttp3:mockwebserver:4.10.0
```

/android-app/module.yaml:
```YAML
product: android/app

apply:
  - ../common.module-template.yaml
  - ../app.module-template.yaml

dependencies:
  - $compose.foundation
  - $compose.material3
```

/ios-app/module.yaml:
```YAML
product: ios/app

apply:
  - ../common.module-template.yaml
  - ../app.module-template.yaml

dependencies:
  - $compose.foundation
  - $compose.material3
```

/jvm-app/module.yaml:
```YAML
product: ios/app

apply:
  - ../common.module-template.yaml
  - ../app.module-template.yaml

dependencies:
  - $compose.desktop.currentOs
```

File layout:
```
|-android-app/
|  |-...
|  |-module.yaml
|-ios-app/
|  |-...
|  |-module.yaml
|-jvm-app/
|  |-...
|  |-module.yaml
|-shared/
|  |-...
|  |-module.yaml
|-common.module-template.yaml
|-app.module-template.yaml
```

Now we can place all common dependencies and settings into the template. Or have multiple templates for various typical configurations in our codebase.

Examples: [templates](../examples/templates)

Documentation:
- [Templates](Documentation.md#templates)

### Further steps

Check the [documentation](Documentation.md) and explore [examples](../examples) for more information.
