- There is currently no Test Runner UI in Fleet.
- Android and iOS application require additional memory for Gradle and Fleet.
  See [new-project-template/gradle.properties](../examples/new-project-template/gradle.properties) and [new-project-template/.fleet/settings.json](../examples/new-project-template/.fleet/settings.json) files. 
- iOS support is highly experimental:
  - iOS applications require at least a single swift file with an `@main` `App`.
  - Opening a project for the first takes long due to indexing.
  - Building a project takes long due to the lack of incrementality and inefficient use of `xcodebuild` tool. 
- [build-variants](../examples/build-variants) example currently doesn't work
