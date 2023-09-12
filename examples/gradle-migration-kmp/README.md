This is an example of combining Gradle build scripts and Pot.yaml
for smooth migration from Gradle.

As you can see three modules are configured differently:
 - `android-app` is configured solely by Gradle build script.
 - `jvm-app` is configured solely by Pot.yaml file.
 - `shared` is configured by both; with pieces of configuration in `Pot.yaml` and `build.gradle.kts` files.
 
Look for the comments inside `shared/build.gradle.kts` and `android-app/build.gradle.kts`.