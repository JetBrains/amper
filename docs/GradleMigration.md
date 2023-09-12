This tutorial demonstrates how to add Pot files to existing Gradle JVM and Kotlin Multiplatform projects.

### Step 0. Prepare

If you want to follow the tutorial:
* Check the [setup instructions](Setup.md)
* Open [a new project template](../examples/new-project-template) in the IDE to make sure everythig works.

Also, see project examples:
* [gradle-interop](../examples/gradle-interop) shows how to use Gradle with an exising Pot.yaml.  
* [gradle-migration-jvm](../examples/gradle-migration-jvm) demonstrates a JVM Gradle project with a Pot module.   
* [gradle-migration-kmp](../examples/gradle-migration-kmp) demonstrates a Kotlin Multiplatform Gradle project with a Pot module.

If you are looking to more detailed info on Gradle interop, check [the documentation](Documentation.md#gradle-interop).


### Step 1. Configure settings.gradle.kts

By default, a basic `gradle.setting.kts` file looks like this:
```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "my-project-name"
```

In order to start using Pot files, add a couple of plugin repositories and apply the plugin:  

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
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:146-NIGHTLY")
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

### Step 3. Create a Pot.yaml file and migrate targets


Cose project (give a link to supported tafget platform)

add a note
Note: to the Gradle project that has Pot yaml 
JVM plugin is not supported, 

    // The following code:
    //   `kotlin("multiplatform") version 1.9.0`
    // should be replaced with:
    //   `kotlin("multiplatform")`

kotlin block:

```
kotlin {
    // everything below 
    android()
    jvm()
    // the rest 

```


# Step 3. Migrate dependencies 


# Step 4. Migrate settings 


# Step 5. Optionally, switch to Pot file layout




