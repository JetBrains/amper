- First project opening in the IDE may take long to download dependencies.   
- Android and iOS application require additional memory for Gradle and Fleet.
  See [new-project-template/gradle.properties](../examples/new-project-template/gradle.properties) and [new-project-template/.fleet/settings.json](../examples/new-project-template/.fleet/settings.json) files. 
- iOS applications support is highly experimental:
  - iOS applications require [at least a single *.swift file](Documentation.md#ios) with an `@main` with an `App` struct and at least a single *.kt file (even a blank one).
  - Opening a project for the first takes long due to indexing.

Please report bugs and suggestions into the [issue tracker](https://youtrack.jetbrains.com/issues/AMPER).