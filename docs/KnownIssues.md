- First project opening in the IDE take long to download dependencies, as currently KMP, Android and Apple Support plugins are always applied.   
- Android and iOS application require additional memory for Gradle and Fleet.
  See [new-project-template/gradle.properties](../examples/new-project-template/gradle.properties) and [new-project-template/.fleet/settings.json](../examples/new-project-template/.fleet/settings.json) files. 
- iOS applications support is highly experimental:
  - iOS applications require at least a single swift file with an `@main` with an `App` struct and at least single even blank *.kt source.
  - Opening a project for the first takes long due to indexing.
  - Building a project takes long due to the lack of incrementality and inefficient use of `xcodebuild` tool. 
- [build-variants](../examples/build-variants) example currently doesn't work
- There is currently no Test Runner UI in Fleet.
