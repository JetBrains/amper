This is an example of combining Gradle build scripts and Pot.yaml
for smooth migration from Gradle to Deft (part 1).

As you can see three modules are configurad differently:
 - `android-app` is configured solely by Gradle build script.
 - `jvm-app` is configured solely by Deft Pot.yaml file.
 - `shared` is configured by both.

In shared module's `Pot.yaml` file there are only platforms and dependencies.
Rest configuration is located in `build.gradle.kts`.

Since no layout is specified in `deft { layout }` section, so
Gradle layout is preserved.

Also, `commonMain` is renamed into just `common`.

Look for comments inside `shared/build.gradle.kts`.

You can search for differences with `multiplatform` example.