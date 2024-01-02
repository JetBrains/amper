import java.nio.file.Path

fun main(args: Array<String>) {
    val module = AndroidModuleData(
        "",
        Path.of("/Users/Anton.Prokhorov/projects/android-sample-jps/out/artifacts/app/app.jar"),
        listOf(
            ResolvedDependency(
                "com.google.guava",
                "guava",
                "33.0.0-android",
                Path.of("/Users/Anton.Prokhorov/projects/android-sample-jps/lib/guava-32.1.3-android.jar")
            ),
//            ResolvedDependency(
//                "lib",
//                "lib",
//                "1.0-SNAPSHOT",
//                Path.of("/Users/Anton.Prokhorov/projects/android-sample-jps/out/artifacts/lib/lib.jar")
//            )
        )
    )
    val lib = AndroidModuleData(
        "library-module",
        Path.of("/Users/Anton.Prokhorov/projects/android-sample-jps/out/artifacts/lib/lib.jar"),
        listOf(),
    )

    // phase 1: generate R-class
    val rClass = run<RClassAndroidBuildResult>(
        AndroidBuildRequest(
            Path.of("/Users/Anton.Prokhorov/projects/android-sample-jps"),
            AndroidBuildRequest.Phase.Prepare,
        ), true
    )
    println(rClass)

    // here: compile using generated R class

    // phase 2: actual build
    val apk = run<ApkPathAndroidBuildResult>(
        AndroidBuildRequest(
            Path.of("/Users/Anton.Prokhorov/projects/android-sample-jps"),
            AndroidBuildRequest.Phase.Build,
            setOf(
                module,
                lib
            ),
        ), true
    )
    println(apk)
}