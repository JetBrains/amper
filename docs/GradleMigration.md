This tutorial demonstrates how to add Amper module files to existing Gradle JVM and Kotlin Multiplatform projects.

See also project examples:
* [gradle-interop](../examples-gradle/gradle-interop) shows how to use Gradle with an exising module.yaml.  
* [gradle-migration-jvm](../examples-gradle/gradle-migration-jvm) demonstrates a JVM Gradle project with an Amper module.   
* [gradle-migration-kmp](../examples-gradle/gradle-migration-kmp) demonstrates a Kotlin Multiplatform Gradle project with an Amper module.

If you are looking for more detailed info on Gradle interop, check [the documentation](Documentation.md#gradle-interop).

### Before you start

Check the [setup instructions](Setup.md).

Note, that:
* JDK 17+ is required. Make sure you have it installed and selected in the IDE. 
* Gradle 8.6 is recommended. Gradle 8.7+ is not currently supported. 
  To make sure your project uses the corresponding Gradle version, 
  check the `./gradle/wrapper/gradle-wrapper.properties` in the root of your project.

### Step 1. Configure settings.gradle.kts

By default, a basic `setting.gradle.kts` file looks like this:
```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "my-project-name"
```

To start using Amper, add a couple of plugin repositories and apply the plugin:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        // add repositories:
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/amper/amper")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}

plugins {
    // apply the plugin:
    id("org.jetbrains.amper.settings.plugin").version("0.4.0-dev-575")
}

rootProject.name = "my-project-name"

// apply the plugin:
plugins.apply("org.jetbrains.amper.settings.plugin")
```

> After this step, the build might fail. That's OK, please proceed to the next step.

### Step 2. Update plugin versions in Gradle scripts 

Certain plugins come preconfigured and their versions can't be changed:

| Plugin                                      | Version     |
|---------------------------------------------|-------------|
| `org.jetbrains.kotlin.multiplatform`        | 2.0.0-RC3   |
| `org.jetbrains.kotlin.android`              | 2.0.0-RC3   |
| `org.jetbrains.kotlin.plugin.serialization` | 2.0.0-RC3   |
| `com.android.library`                       | 8.2.2       |
| `com.android.application`                   | 8.2.2       |
| `org.jetbrains.compose`                     | 1.6.10-rc01 |

Check the `settings.gradle.kts` file and update `pluginManagement { plugins {...} }` section:
```kotlin
pluginManagement {
    ...
    plugins {
        kotlin("multiplatform").version(...)
        kotlin("android").version(...)
        id("com.android.base").version(...)
        id("com.android.application").version(...)
        id("com.android.library").version(...)
        id("org.jetbrains.compose").version(...)
    }
}
```
And update them to:
```kotlin
pluginManagement {
    ...
    plugins {
        kotlin("multiplatform")
        kotlin("android")
        id("com.android.base")
        id("com.android.application")
        id("com.android.library")
        id("org.jetbrains.compose")
    }
}
```

Then, check all `build.gradle.kts` `plugins` section like this:
```kotlin
plugins {
    kotlin("multiplatform") version "..." 
    id("org.jetbrains.compose") version "..."
    application
}
```

And update them to:
```kotlin
plugins {
    kotlin("multiplatform") 
    id("org.jetbrains.compose")
}
```

After this step, you should be able to build the project without errors.
If there are problems with the builds, check the previous steps and if they don't help, report the problem
to [our issue tracker](https://youtrack.jetbrains.com/issues/AMPER).

### Step 3. Create a module.yaml file and migrate targets

As the next step, choose a Gradle subproject that you want to start with.
It could be a shared library or an application, such as JVM, Android, iOS, or native. Check the full list
of [the supported product types](Documentation.md#product-types)

#### JVM projects

Add a `module.yaml` file next to the corresponding `build.gradle.kts`:

```
|-src/
|  |-main/
|  |  |-kotlin
|  |  |  |-main.kt
|  |  |-resources
|  |  |  |-...
|  |-test/
|-module.yaml
|-build.gradle.kts
```

module.yaml:

```yaml
# Produce a JVM library
product:
  type: lib
  platforms: [jvm]

# Enable Gradle-compatible file layout 
module:
  layout: gradle-jvm
```

The `product:` section controls the type of produced artifact, in this case, a library for the JVM platform.
The `layout: gradle-jvm` enables a [Gradle-compatible mode](Documentation.md#file-layout-with-gradle-interop) for JVM
projects.

> Due to current limitation, when you migrate a JVM subproject to an Amper module you need to replace
> the `org.jetbrains.kotlin.jvm` plugin with `org.jetbrains.kotlin.multiplatform`.

Find code like

```kotlin
plugins {
    ...
    kotlin("jvm")
    ...
}
```

And update to:

```kotlin
plugins {
    ...
    kotlin("multiplatform")
    ...
}
```

See example project [gradle-migration-jvm](../examples-gradle/gradle-migration-jvm).

#### Kotlin Multiplatform projects

Add a `module.yaml` file next to the corresponding `build.gradle.kts`:

```
|-src/
|  |-commonMain/
|  |  |-kotlin
|  |  |  |-main.kt
|  |  |-resources
|  |  |  |-...
|  |-commonTest/
|  |-jvmMain/
|  |-jvmTest/
|  |-androidMain/
|  |-androidTest/
|-module.yaml
|-build.gradle.kts
```

module.yaml:

```yaml
# Produce a JVM library
product:
  type: lib
  platforms: [jvm, android]

# Enable Gradle-compatible Multiplatform file layout 
module:
  layout: gradle-kmp
```

The `product:` section controls the type of produced artifact, in this case, a library for the JVM and for Android
platforms. The `layout: gradle-kmp` enables a [Gradle-compatible mode](Documentation.md#file-layout-with-gradle-interop)
for Kotlin Multiplatform projects.

After creating a module.yaml file, remove
the [Kotlin targets section](https://kotlinlang.org/docs/multiplatform-set-up-targets.html) from your Gradle build
script, since they are configured in module.yaml:

```kotlin
kotlin {
    // Remove the following lines
    android()
    jvm()
    ...


  // Leave the source set configuration as is:
    sourceSets {
        val commonMain by getting {
            dependencies {
                ...
            }
        }
        val commonTest by getting 
        val jvmMain by getting
        val jvmTest by getting
        ...
    }
}
```

After this step, you should be able to build the project.

# Step 4. Migrate dependencies 

The next step is to migrate the dependencies. See the [details on the dependency syntax](Documentation.md#dependencies).

Let's take a typical dependencies section:
```kotlin
dependencies {
    api(":api") // API dependency on a sub-project
    implementation("io.ktor:ktor-client-core:2.3.2") // regular Maven dependency
    implementation(libs.gson) // dependency from the Gradle libs.versions.toml version catalog
    implementation(compose.desktop.currentOS) // dependency provided by the Compose Multiplatform plugin
    testImplementation(kotlin("test")) // test dependency on a Kotlin Test library
    testImplementation(":test-utils") // test dependency on a sub-project 
}
```

Here is how it maps to the Amper module DSL:
```yaml
dependencies:
  - ../api: exported    
  - io.ktor:ktor-client-core:2.3.2
  - $libs.gson
  - $compose.desktop.currentOS

test-dependencies:
  - ../test-utils
```

Note several things here:

* The example assumes that `api` and `test-utils` modules can be found at the corresponding relative paths.
  See [details on the internal dependencies](Documentation.md#internal-dependencies).
* Gradle's `api()` dependency is mapped to `exported` dependency attribute.
  See [details on scopes and visibility](Documentation.md#scopes-and-visibility).
* Use of cataloged dependencies requires a `$` prefix, so Gradle's `libs.gson` becomes `$libs.gson` in Amper.
* You don't need to add a `kotlin("test")` dependency as it is added automatically.

In Kotlin Multiplatform projects, it is typical that certain target platforms have their own dependencies. So the similar list of dependencies could look like this:  
```kotlin
kotlin {
    //...
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(":api")
                implementation("io.ktor:ktor-client-core:2.3.2")
                implementation(libs.gson)
            }
        }
        val commonTest by getting {
            dependencies {
                testImplementation(kotlin("test"))
                testImplementation(":test-utils")
            }
        } 
        
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-java:2.3.2")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:2.3.3")
            }
        }
        ...
    }
}
```

Here is how it maps to the Amper module DSL:
```yaml

dependencies:
  - ../api: exported  
  - io.ktor:ktor-client-core:2.3.2
  - $libs.gson

dependencies@jvm:
  - io.ktor:ktor-client-java:2.3.2
  
dependencies@android:
  - io.ktor:ktor-client-okhttp:2.3.2

test-dependencies:
  - ../test-utils
```

Note how the platform-specific dependency blocks have [@platform qualifier](Documentation.md#platform-qualifier).

# Step 5. Migrate settings

Settings like a Kotlin language version, Java release version, Android sdk versions could be moved to the `settings:`
section in the module configuration file. E.g., for the following Gradle script:

```kotlin
kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.example"
    compileSdk = "android-34"
}

```

These settings would look like this in a module.yaml file:
```yaml
settings:
  jvm:
    release: 17     
  android:
    namespace: com.example
    compileSdk: 34
```

See the [full list of supported settings](DSLReference#compose).

# Step 6. Optionally, switch to the Amper file layout

So far, we have only changed the `module.yaml` and `build.gradle.kts` files and didn't change the source layout.
Such a gradual transition was possible because at [step 3](#step-3-create-a-moduleyaml-file-and-migrate-targets) we
explicitly set the Gradle-compatibility layout mode
```yaml
...
# Enable Gradle-compatible file layout 
module:
  layout: gradle-jvm
...
```

As the next optional step, you may also consider migrating to the [lightweight layout](Documentation.md#project-layout):
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

To do so, you need to rearrange the sources folders according to [these tables](Documentation.md#gradle-vs-amper-project-layout), and disable the Gradle compatibility mode.
To enable the Amper layout, set `layout:` to `default` or remove the section:
```yaml
product:
  type: lib
  platforms: [jvm, android]

module:
  layout: default
...
```

and remove the source set configuration from your `build.gradle.kts` file:
```kotlin
kotlin {
    // Remote the following block
    sourceSets {
        val commonMain by getting {
            ...
        }
        val commonTest by getting {
            ...
        } 
        val jvmMain by getting {
            ...
        }
        val androidMain by getting {
            ...
        }
        ...
    }
}

```

# Step 7. Migrate other Gradle subprojects

After the previous step, you have your Gradle subproject fully migrated to Amper. You may now consider migrating the
rest of the subprojects.
