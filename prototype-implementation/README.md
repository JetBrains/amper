//TODO Need to review

# TL;DR
Launch `publishAllToLocal` gradle task on `test-kmodules-modules` project.
Then import one of `[facade-examples](facade-examples)` projects.

# Description
To test toml model you need to import one of `[facade-examples](facade-examples)`
gradle projects.

In oder to do so, you need to specify dependencies on model and dependency on facade 
binding, and then apply plugin from binding in `settings.gradle.kts` file of an `facade-examples`
subdirectory.

These dependencies should be obtained from local maven repo (because of its simplicity).
To place them there, `publishAllToLocal` task can be executed.

You can have something like that in `setting.gradle.kts`:
```kotiln
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath("org.example:01-simple-model:1.0-SNAPSHOT")
        classpath("org.example:1-gradle-facade-binding:1.0-SNAPSHOT")
    }
}

plugins.apply("org.example.settings.plugin")
```

The binding library specifies how to integrate chosen model with build system.

The model library specifies how to parse chosen model with build system.

As you can see in `[0-core-model](0-core-model)` - java services are used to separate
model parsing implementation from actual model, so you can replace model library
with any other, that supports service from `[0-core-model](0-core-model)`.

For now on, only model from `0-core-model` is supported, but any other model can be implemented
in the same way to test it.