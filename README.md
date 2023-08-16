**Quick start:**

* Check [setup instructions](docs/Setup.md)
* Open the checked out `<deft-prototype>/` folder in IntelliJ IDEA.

**Project structure:**

As you can see [frontend-api](prototype-implementation/frontend-api) - java services are used to separate
model parsing implementation from actual model, so you can replace model library
with any other, see [frontend](prototype-implementation/frontend) subdirectories.

Actual IJ Deft plugin is located [in the intellij repository](https://jetbrains.team/p/ij/repositories/intellij/files/d938effc4b30bb213b939051ceec78bf4e0c2e6d/plugins/deft) and bundled automatically.
IJ plugin for previous IDE versions is in [ide-plugin-231-232](prototype-implementation/ide-plugin-231-232). 

**Using locally built Gradle plugin:**

Launch `:publishToMavenLocal` gradle task on `prototype-implementation` project.
Then import `./prototype-implementation/try` project.

**Using Gradle plugins from sources:**  

Change `setting.gradle.kts` to:

```kotiln
pluginManagement {
    includeBuild("<relative path to ./prototype-implementation")
}

plugins {
    id("org.jetbrains.deft.proto.settings.plugin")
}
```
