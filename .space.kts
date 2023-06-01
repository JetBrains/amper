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
    scriptBody: ScriptApi.() -> Unit,
) = job(name) {
    if (customTrigger != null) {
        startOn { customTrigger() }
    }
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
    customTrigger = { schedule { cron("0 0 * * *") } }
) {
    // Crumble-some and nasty version replacement.
    // FIXME Replace it with templates when they will be available!
    File(".").walkTopDown()
        .forEach { file ->
            if (file.name == "Pot.yaml") {
                val newVersion = "version: ${executionNumber()}"
                val oldVersion = "version: 1.0-SNAPSHOT"
                val newContent = file.readText().replace(oldVersion, newVersion)
                file.writeText(newContent)
            }
        }

    // Do the work.
    gradlew(
        "--info",
        "--stacktrace",
        "test",
        "publishAllPublicationsToScratchRepository",
        "--fail-fast",
        "-q"
    )
}