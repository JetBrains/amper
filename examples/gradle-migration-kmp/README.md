This is an example of combining Gradle build scripts and Deft Modules
for smooth migration from Gradle.

As you can see three modules are configured differently:
 - `android-app` is configured solely as a Gradle build script.
 - `jvm-app` is configured solely as a Deft Module file.
 - `shared` is configured by both; with pieces of configuration in `module.yaml` and `build.gradle.kts` files.
 
Look for the comments inside `shared/build.gradle.kts` and `android-app/build.gradle.kts`.