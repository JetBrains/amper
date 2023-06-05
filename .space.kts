import circlet.pipelines.script.ScriptApi
import java.io.File

fun ScriptApi.addCreds() {
    File("root.local.properties").writeText(
        """
                scratch.username=${spaceClientId()}
                scratch.password=${spaceClientSecret()}
            """.trimIndent()
    )
}

fun `prototype implementation job`(
    name: String,
    customTrigger: (Triggers.() -> Unit)? = null,
    customParameters: Parameters.() -> Unit = {  },
    scriptBody: ScriptApi.() -> Unit,
) = job(name) {
    if (customTrigger != null) startOn { customTrigger() }
    parameters { customParameters() }
    container(displayName = name, image = "amazoncorretto:17") {
        workDir = "prototype-implementation"
        kotlinScript {
            it.addCreds()
            it.scriptBody()
        }
    }
}

// Common build for every push.
`prototype implementation job`("Build") {
    gradlew(
        "--info",
        "--stacktrace",
        "test",
        "--fail-fast",
        "-q"
    )
}

// Nightly build for auto publishing.
`prototype implementation job`(
    "Build and publish",
    customTrigger = { schedule { cron("0 0 * * *") } },
    customParameters = { text("version", value = "") }
) {
    // Crumble-some and nasty version replacement.
    // FIXME Replace it with templates when they will be available!
    val file = File("common.Pot.yaml")
    val nightlyVersion = "${executionNumber()}-NIGHTLY-SNAPSHOT"
    val newVersion = "version: ${parameters["version"]?.takeIf { it.isNotBlank() } ?: nightlyVersion}"
    val oldVersion = "version: 1.0-SNAPSHOT"
    val newContent = file.readText().replace(oldVersion, newVersion)
    file.writeText(newContent)

    // Do the work.
    gradlew(
        "--info",
        "--stacktrace",
        "--quiet",
        "test",
        "--fail-fast",
        "publishAllPublicationsToScratchRepository",
    )
}