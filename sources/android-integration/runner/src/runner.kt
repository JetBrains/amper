import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.tooling.GradleConnector
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

inline fun <reified R : AndroidBuildResult> runAndroidBuild(buildRequest: AndroidBuildRequest, debug: Boolean = false): R {
    // todo: temp directory isn't the best place for such temporary project, because we don't utilize gradle caches,
    //  but ok for debug
    val tempDir = createTempDirectory()
    val settingsGradle = tempDir.resolve("settings.gradle.kts")
    val settingsGradleFile = settingsGradle.toFile()
    settingsGradleFile.createNewFile()
    // todo: hide by feature flag building plugin from source
    settingsGradleFile.writeText(
        """
pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        includeBuild("${Path.of("../../../").toAbsolutePath().normalize()}")
    }
}


plugins {
    id("org.jetbrains.amper.android.settings.plugin")
}

configure<AmperAndroidIntegrationExtension> {
    jsonData = ${"\"\"\""}${Json.encodeToString(buildRequest)}${"\"\"\""}
}
""".trimIndent()
    )

    val connection = GradleConnector
        .newConnector()
        .forProjectDirectory(settingsGradleFile.parentFile)
        .connect()

    val tasks = buildList {
        for (buildType in buildRequest.buildTypes) {
            val taskPrefix = when (buildRequest.phase) {
                AndroidBuildRequest.Phase.Prepare -> "prepare"
                AndroidBuildRequest.Phase.Build -> "assemble"
            }
            val taskBuildType = buildType.name
            val taskName = "$taskPrefix$taskBuildType"
            if (buildRequest.targets.isEmpty()) {
                add(taskName)
            } else {
                for (target in buildRequest.targets) {
                    if (target == ":") {
                        add(":$taskName")
                    } else {
                        add("$target:$taskName")
                    }
                }
            }
        }
    }.toTypedArray()

    val buildLauncher = connection
        .action { controller -> controller.getModel(R::class.java) }
        .forTasks(*tasks)
        .withArguments("--stacktrace")
        .setStandardOutput(System.out)
        .setStandardError(System.err)

    if (debug) {
        buildLauncher.addJvmArguments("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
    }
    return buildLauncher.run()
}