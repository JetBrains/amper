import circlet.pipelines.script.ScriptApi
import java.io.File

private val tbePluginTokenEnv = "TBE_PLUGIN_TOKEN"

fun ScriptApi.addCreds() {
    File("root.local.properties").writeText(
        """
                scratch.username=${spaceClientId()}
                scratch.password=${spaceClientSecret()}
                ide-plugin.publish.token=${System.getenv(tbePluginTokenEnv)}
            """.trimIndent()
    )
}

fun `prototype implementation job`(
    name: String,
    customTrigger: (Triggers.() -> Unit)? = null,
    customParameters: Parameters.() -> Unit = { },
    customContainerBody: Container.() -> Unit = { },
    scriptBody: ScriptApi.() -> Unit,
) = job(name) {
    if (customTrigger != null) startOn { customTrigger() }
    parameters { customParameters() }
    container(displayName = name, image = "thyrlian/android-sdk") {
        workDir = "prototype-implementation"
        customContainerBody()
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

// Build for publishing plugin.
`prototype implementation job`(
    "Plugin (Build and publish)",
    customTrigger = { /* Will add cron when checked */ },
    customParameters = { secret("tbe.plugin.token", value = "{{ project:tbe.plugin.token }}") },
    customContainerBody = { env[tbePluginTokenEnv] = "{{ tbe.plugin.token }}" }
) {
    val file = File("gradle.properties")
    val nightlyVersion = "${executionNumber()}-NIGHTLY-SNAPSHOT"
    val newVersion = "ide-plugin.version=${parameters["version"]?.takeIf { it.isNotBlank() } ?: nightlyVersion}"
    val oldVersion = "ide-plugin.version=0.2-SNAPSHOT"
    val newContent = file.readText().replace(oldVersion, newVersion)
    file.writeText(newContent)

    gradlew(
        "--info",
        "--stacktrace",
        "--quiet",
        "publishPlugin",
    )
}
