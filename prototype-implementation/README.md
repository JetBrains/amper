//TODO Need to review

# TL;DR
Launch `:publishToMavenLocal` gradle task on `prototype-implementation` project.
Then import [try](try) project.
                      
Check [setup instructions]()
Fo Nightly IDEA plugin updates add [this repository](https://tbe.labs.jb.gg/api/plugin-repository?pluginId=org.jetbrains.deft&channel=Nightly) to the list in `IntelliJ IDEA | Settings | Plugins | Manage plugin repositories...`

# Description
To test toml model you need to import [try](try) gradle project.

In oder to do so, you need to specify dependencies on model and dependency on facade 
binding, and then apply plugin from binding in `settings.gradle.kts` file.

These dependencies should be obtained from local maven repo (because of its simplicity).
To place them there, `:publishToMavenLocal` on root project can be executed.

You can have something like that in `setting.gradle.kts`:
```kotiln
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.deft.proto.frontend:frontend-api:1.0-SNAPSHOT")
        classpath("org.jetbrains.deft.proto.frontend.without-fragments.yaml:yaml:1.0-SNAPSHOT")
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:1.0-SNAPSHOT")
        classpath("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.8.10")
    }
}

plugins.apply("org.jetbrains.deft.proto.settings.plugin")
```

The binding library specifies how to integrate chosen model with build system.

The model library specifies how to parse chosen model with build system.

As you can see in [frontend-api](frontend-api) - java services are used to separate
model parsing implementation from actual model, so you can replace model library
with any other, see [frontend](frontend) subdirectories.