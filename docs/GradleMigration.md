This tutorial demonstrates how to add Deft Module files to existing Gradle JVM and Kotlin Multiplatform projects.

If you want to follow the tutorial:
* Check the [setup instructions](Setup.md)
* Open [a new project template](../examples/new-project-template) in the IDE to make sure everything works.

Also, see project examples:
* [gradle-interop](../examples/gradle-interop) shows how to use Gradle with an exising module.yaml.  
* [gradle-migration-jvm](../examples/gradle-migration-jvm) demonstrates a JVM Gradle project with a Deft Module.   
* [gradle-migration-kmp](../examples/gradle-migration-kmp) demonstrates a Kotlin Multiplatform Gradle project with a Deft Module.

If you are looking to more detailed info on Gradle interop, check [the documentation](Documentation.md#gradle-interop).

### Step 0. Prepare

* JDK 17+ is required. Make sure you have it installed and selected in the IDE. 
* Gradle 8.1.1+ is recommended. Make sure your project uses the corresponding Gradle version, 
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

In order to start using Deft, add a couple of plugin repositories and apply the plugin:  

```kotlin
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        
        // add repositories:
        google()
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }

    // add plugin classpath:
    dependencies {
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:185-NIGHTLY")
    }
}

rootProject.name = "my-project-name"

// apply the plugin:
plugins.apply("org.jetbrains.deft.proto.settings.plugin")
```

_Note: after this step the build might fail. That's OK, please proceed to the next step._ 

### Step 2. Update plugin versions in Gradle scripts 

Certain plugins come preconfigured and their versions can't be changed. Here is the list:
* `org.jetbrains.kotlin.multiplatform`
* `org.jetbrains.kotlin.android`
* `com.android.library`
* `com.android.application`
* `org.jetbrains.compose`

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
And updated them to:
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

And updated them to:
```kotlin
plugins {
    kotlin("multiplatform") 
    id("org.jetbrains.compose")
}
```

After this step you should be able to build the project without errors.
If there are problems with the builds, check the previous steps and if they don't help, report the problem.

### Step 3. Create a module.yaml file and migrate targets

As the next step, chose a Gradle subproject that you want to start with.
It could be a shared library or an application, such as JVM, Android, iOS, or native. Check the full lis
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

_Note: Due to current limitation, when you migrate a JVM subproject to a Deft Module you need to replace
the `org.jetbrains.kotlin.jvm` plugin with `org.jetbrains.kotlin.multiplatform`._
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

See example project [gradle-migration-jvm](../examples/gradle-migration-jvm).

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
platforms.
The `layout: gradle-kmp` enables a [Gradle-compatible mode](Documentation.md#file-layout-with-gradle-interop) for Kotlin
Multiplatform
projects.

After creating a module.yaml file, remove the [Kotlin targets section](https://kotlinlang.org/docs/multiplatform-set-up-targets.html) from your Gradle build script, since they are configured in module.yaml:
```kotlin
kotlin {
    // Remove the following lines
    android()
    jvm()
    ...
    
    
    // But leave the source set configuration as is:
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

After this step you should be able to build the project.

# Step 4. Migrate dependencies 

The next step is to migrate the dependencies. See the [details on the dependencies syntax](Documentation.md#dependencies).

Let's take a typical dependencies section:
```kotlin
dependencies {
    api(":api")
    implementation("io.ktor:ktor-client-core:2.3.2")
    implementation("io.ktor:ktor-client-java:2.3.2")
    testImplementation(kotlin("test"))
    testImplementation(":test-utils")
}
```

Here is how it maps to the Deft Module DSL:
```yaml
dependencies:
  - ../api: exported  
  - io.ktor:ktor-client-core:2.3.2
  - io.ktor:ktor-client-java:2.3.2

test-dependencies:
  - ../test-utils
```

Note several things here:
* The example assumes that `api` and `test-utils` modules can be found the at the corresponding relative paths. See [details on the internal dependencies](Documentation.md#internal-dependencies).
* Gradle's `api()` dependency is mapped to `exported` dependency attribute. See [details on scopes and visibility](Documentation.md#scopes-and-visibility).   
* You don't need to add a `kotlin("test")` dependency as it is added automatically.

In Kotlin Multiplatform projects is typical that certain target platform have their own dependencies. So the similar list of dependencies could look like this:  
```kotlin
kotlin {
    //...
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(":api")
                implementation("io.ktor:ktor-client-core:2.3.2")
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

Here is how it maps to the Deft Module DSL:
```yaml

dependencies:
  - ../api: exported  
  - io.ktor:ktor-client-core:2.3.2

dependencies@jvm:
  - io.ktor:ktor-client-java:2.3.2
  
dependencies@android:
  - io.ktor:ktor-client-okhttp:2.3.2

test-dependencies:
  - ../test-utils
```

Note, how the platform-specific dependency blocks have [@platform qualifier](Documentation.md#platform-qualifier). 

# Step 5. Migrate settings

Settings like Kotlin language version, Java target/source version, Android sdk versions could be moved to the `settings:` section in the Module Manifest.
E.g. for the following Gradle script:

```kotlin
kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.example"
    compileSdkVersion = "android-34"
}

```

These settings would look like in a module.yaml file:
```yaml
settings:
  jvm:
    target: 17     
  android:
    namespace: com.example
    compileSdkVersion: android-34
```

See the [full list of supported settings](DSLReference#compose).

# Step 6. Optionally, switch to the Deft file layout

So far, we have only changed the `module.yaml` and `build.gradle.kts` files and didn't change the source layout.
Such gradual transition was possible because at the [step 3](#step-3-create-a-moduleyaml-file-and-migrate-targets) we explicitly set the Gradle-compatibility layout mode
```yaml
...
# Enable Gradle-compatible file layout 
module:
  layout: gradle-jvm
...
```

As the next optional step you may also consider to migrate to the [lightweight layout](Documentation.md#project-layout):
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

To do so, you need to rearrange the sources folders according to [these tables](Documentation.md#gradle-vs-deft-project-layout), and disable the Gradle compatibility mode.
To enable the Deft layout, set `layout:` to `default` or simply remove the section:
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

After the previous step you have your Gradle subproject fully migrated to Deft. You may now consider to migrate the rest of the subprojects.