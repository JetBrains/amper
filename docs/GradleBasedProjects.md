# Gradle-based Amper projects

Amper is currently also published as a Gradle plugin. This allows using Gradle as the main build tool, while still 
benefiting from Amper's leaner and more toolable configuration files.

This document highlights some specifics of working with Amper in a Gradle-based project.

> [!WARNING]
> Using Amper as a Gradle plugin is now deprecated. We recommend migrating to the standalone Amper build tool instead.
> You can check the specifics of the Amper CLI in the [CLI usage documentation](Usage.md), and refer to the regular
> [documentation](Documentation.md) for general information.

## Requirements

To use the Gradle-based version of Amper:
 * JDK 17+ is required.
 * Gradle 8.7+ is required. To make sure your project uses the desired Gradle version,
   check the `./gradle/wrapper/gradle-wrapper.properties` in the root of your project.

## Migrating an existing Gradle project to Amper

See the [Gradle migration guide](GradleMigration.md).

## Using Gradle-based Amper from the command line

When using Amper as a Gradle plugin, the main build tool is still Gradle and the entry point is therefore the same
`./gradlew` script as before.

For example, to build and run the [JVM "Hello, World"](../examples-gradle/jvm) example:
```
cd jvm
./gradlew run 
```

See the [Gradle tutorial](https://docs.gradle.org/current/samples/sample_building_java_applications.html) for more info.

Similarly, to package Android applications, you can use the Gradle tasks provided by the Android Gradle plugin instead
of the Amper CLI packaging commands.

## Differences with standalone Amper

### Multi-module projects

In a Gradle-based project, instead of a `project.yaml` file, you should use the regular `settings.gradle.kts` file to
register all subprojects (or "modules" in Amper terminology), including the ones that are configured using Amper module 
files. This is done with the `include()` function:

```kotlin
// Amper repositories setup
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        maven("https://packages.jetbrains.team/maven/p/amper/amper")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}
// Amper plugin registration
plugins {
    id("org.jetbrains.amper.settings.plugin").version("0.8.0-dev-2856")
}

// Add both Gradle and Amper modules to the project
include("app", "lib")
```

### Module file layout

In Gradle-based projects, some Gradle-compatible layouts are supported by Amper:

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
Read more about [Gradle-compatible project layouts](#file-layout-with-gradle-interoperability) below.

### Repositories declaration

The [maven repositories](Documentation.md#managing-maven-repositories) section of the general documentation describes
how repositories are declared in Amper, but there is some specific behavior to watch out for when using Gradle.

If some repositories are defined in `settings.gradle.kts` using a `dependencyResolutionManagement` block, they are only
taken into account by pure Gradle subprojects, and don't affect Amper modules. If you want to define repositories in a
central place for Amper modules, you can use a `repositories` list in a [template file](Documentation.md#templates) and
apply this template to your modules.

Technical explanation: in Gradle, adding any repository at the subproject level will by default discard the repositories
configured in the settings (unless a different Gradle
[RepositoriesMode](https://docs.gradle.org/current/javadoc/org/gradle/api/initialization/resolve/RepositoriesMode.html)
is used). Default repositories provided by Amper is an equivalent to adding a `repositories` section in
the `build.gradle.kts` file of each Amper module.

### Compose multiplatform

When building Compose Multiplatform projects, a little bit of customization is needed in the `gradle.properties` when 
using Gradle, because Android and iOS builds require more memory:

```properties
# Compose requires AndroidX
android.useAndroidX=true

# Android and iOS build require more memory, so we set -Xmx4g.
# The other options are just Gradle defaults that we restore because they are overridden as soon as we use this property
org.gradle.jvmargs=-Xmx4g -Xms256m -XX:MaxMetaspaceSize=384m -XX:+HeapDumpOnOutOfMemoryError
```

If the `gradle.properties` file doesn't exist, create it in the root of the project:
```
|-...
|-settings.gradle.kts
|-gradle.properties 
```

#### Android

When developing Android apps in Gradle, a `local.properties` file is required to configure the Android SDK location.
Create it in the root of the project with the following content:

 ```properties 
 ## This file must *NOT* be checked into Version Control Systems
sdk.dir=<path to the Android SDK>
 ```

> [!TIP]
> Here is how the path usually looks like:
> 
> * On macOS: `/Users/<username>/Library/Android/sdk`
> * On Linux: `/home/<username>/Android/Sdk`
> * On Windows: `C:\Users\<username>\AppData\Local\Android\Sdk`


#### Using multiplatform resources

To use multiplatform resources in a Gradle-based Amper project, a module must be configured with a
[Gradle-compatible file layout](#file-layout-with-gradle-interoperability), and you should place resources this way:
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

## Gradle interoperability

The Gradle interoperability supports the following scenarios:

* partial use of Amper in an existing Gradle project,
* smooth and gradual [migration of an existing Gradle project](./GradleMigration.md) to Amper,
* writing custom Gradle tasks or using existing Gradle plugins in an existing Amper module.

Gradle features supported in Amper modules:
* Cross-project dependencies between Gradle sub-projects and Amper modules.
* Using the default [libs.versions.toml version catalogs](https://docs.gradle.org/current/userguide/platforms.html#sub:conventional-dependencies-toml).
* Writing Gradle [custom tasks](#writing-custom-gradle-tasks).
* Using [Gradle plugins](#using-gradle-plugins).
* Configuring additional [settings in the `build.gradle.kts` files](#configuring-settings-in-the-gradle-build-files).
* [Gradle-compatible file layout](#file-layout-with-gradle-interoperability).

To use Gradle interop in an Amper module, place either a `build.gradle.kts` or a `build.gradle` file next to
your `module.yaml` file:
```
|-src/
|  |-main.kt
|-module.yaml
|-build.gradle.kts
```

### Writing custom Gradle tasks

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


### Using Gradle plugins

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

### Configuring settings in the Gradle build files

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

### File layout with Gradle interoperability

The default [module layout](Documentation.md#project-layout) suits best for the newly created modules:
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
 - `default`: Amper ['flat' file layout](Documentation.md#project-layout) is used. Source folders configured in the Gradle script are not available.  
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

In the compatibility mode, source sets can be configured or amended in the Gradle script.
They are accessible by their names: `commonMain`, `commonTest`, `jvmMain`, `jvmTest`, etc.:
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

Additionally, source sets are generated for each [alias](Documentation.md#aliases).
For example, given the following module configuration:

```yaml
product:
  type: lib
  platforms: [android, jvm]
  
module:
  layout: gradle-kmp

aliases:
  - jvmAndAndroid: [jvm, android]
```

two source sets are generated (`jvmAndAndroid` and `jvmAndAndroidTest`) and can be used as follows:

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


See also documentation on [Kotlin Multiplatform source sets](https://kotlinlang.org/docs/multiplatform-discover-project.html#source-sets)
and [custom source sets configuration](https://kotlinlang.org/docs/multiplatform-dsl-reference.html#custom-source-sets).

