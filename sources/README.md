**Quick start:**

* Check [setup instructions](../docs/Setup.md)
* Open the checked out `<amper>/` directory in IntelliJ IDEA or Fleet

**Using locally built Gradle plugin:**

Launch `:publishToMavenLocal` gradle task.

**Using Gradle plugins from sources:**  

Change `setting.gradle.kts` to:

```kotiln
pluginManagement {
    includeBuild("<relative path to checkout root>")
}

plugins {
    id("org.jetbrains.amper.settings.plugin")
}
```
