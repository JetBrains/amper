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
    val result = run(
        AndroidBuildRequest(
            Path.of("/Users/Anton.Prokhorov/projects/android-sample-jps"),
            listOf(
                module,
                lib
            ),
            AndroidBuildRequest.BuildType.Debug,
            AndroidBuildRequest.Phase.Build
        ), true
    )

    println(result)
}