import java.nio.file.Path

fun main(args: Array<String>) {
    val app = AndroidModuleData(
        ":",
        Path.of("/Users/Anton.Prokhorov/projects/android-sample-jps/out/production/android-sample-jps"),
        listOf(
            ResolvedDependency(
                "com.google.guava",
                "guava",
                "33.0.0-android",
                Path.of("/Users/Anton.Prokhorov/projects/android-sample-jps/lib/guava-32.1.3-android.jar")
            ),
            ResolvedDependency(
                "org.jetbrains.kotlin",
                "kotlin-stdlib",
                "1.9.21",
                Path.of("/Users/Anton.Prokhorov/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib/1.9.21/kotlin-stdlib-1.9.21.jar")
            ),
            ResolvedDependency(
                "lib",
                "lib",
                "1.0-SNAPSHOT",
                Path.of("/private/var/folders/xw/z_93hx814_b9lj8cqlrgfhb80000gp/T/11319250380321512478/build/outputs/aar/11319250380321512478-debug.aar")
            )
        )
    )
    val lib = AndroidModuleData(
        ":library-module",
        Path.of("/Users/Anton.Prokhorov/projects/android-sample-jps/out/production/library-module"),
    )

    val emptyApp = AndroidModuleData(":")
    val emptyLib = AndroidModuleData(":library-module")

    // phase 1: generate R-class
    val rClass = run<RClassAndroidBuildResult>(
        AndroidBuildRequest(
            Path.of("/Users/Anton.Prokhorov/projects/android-sample-jps"),
            AndroidBuildRequest.Phase.Prepare,
            setOf(emptyApp, emptyLib),
        )
    )
    println(rClass)

    // here: compile using generated R class

    // phase 2: actual build
    val apk = run<ApkPathAndroidBuildResult>(
        AndroidBuildRequest(
            Path.of("/Users/Anton.Prokhorov/projects/android-sample-jps"),
            AndroidBuildRequest.Phase.Build,
            setOf(
                app,
                lib,
            ),
        ),
    )
    println(apk)
}