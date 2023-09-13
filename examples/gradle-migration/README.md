This is an example of combining Gradle build scripts and Pot.yaml
for smooth migration from Gradle to Deft.

As you can see three modules are configured differently:
 - `android-app` is configured solely by Gradle build script.
 - `jvm-app` is configured solely by Deft Pot.yaml file.
 - `shared` is configured by both.

Settings are shared between `Pot.yaml` and `build.gradle.kts` for the sake of example.

There is a specified `gradle-kmp` layout in `Pot.yaml`.

Look for comments inside `shared/build.gradle.kts`.

You can search for differences with `multiplatform` example.