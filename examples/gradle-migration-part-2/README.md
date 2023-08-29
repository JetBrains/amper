This is an example of combining Gradle build scripts and Pot.yaml
for smooth migration from Gradle to Deft (part 2).

As you can see three modules are configurad differently:
 - `android-app` is configured solely by Gradle build script.
 - `jvm-app` is configured solely by Deft Pot.yaml file.
 - `shared` is configured by both.

The difference from the first part is that now shared module 
use `COMBINED` layout, so only custom source sets are preserved.

Also, `src` (common source set within Deft layout) is renamed into 
just `src@common` to prevent messin with `src/util/kotlin`.

Look for comments inside `shared/build.gradle.kts`.

You can search for differences with `multiplatform` example.