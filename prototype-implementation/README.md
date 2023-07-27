**Quick start:**

* Check [setup instructions](/docs/Setup.md)
* Open the checked out `<deft-prototype>/` folder in IntelliJ IDEA.

**Project structure:**

As you can see [frontend-api](frontend-api) - java services are used to separate
model parsing implementation from actual model, so you can replace model library
with any other, see [frontend](frontend) subdirectories.

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
