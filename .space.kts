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
        "allTests"
    )
}

// Nightly build for auto publishing.
`prototype implementation job`(
    "Build and publish",
    customTrigger = { schedule { cron("0 0 * * *") } },
    customParameters = {
        text("channel", value = "Nightly") {
            options("Stable", "Nightly")
        }
    }
) {
    val newVersion = ChannelAndVersion.from(this).version

    File("common.Pot-template.yaml").apply {
        val updated = readText().replace("version: 1.0-SNAPSHOT", "version: $newVersion")
        writeText(updated)
    }

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
    "Intellij plugin (Build and publish)",
    customTrigger = { schedule { cron("0 0 * * *") } },
    customParameters = {
        text("channel", value = "Nightly") {
            options("Stable", "Nightly")
        }
        secret("tbe.plugin.token", value = "{{ project:tbe.plugin.token }}", description = "Toolbox Enterprise token for publishing")
    },
    customContainerBody = { env[tbePluginTokenEnv] = "{{ tbe.plugin.token }}" }
) {
    val (newChannel, newVersion) = ChannelAndVersion.from(this)

    File("gradle.properties").apply {
        val updated = readText()
            .replace("ide-plugin.version=1.0-SNAPSHOT", "ide-plugin.version=$newVersion")
            .replace("ide-plugin.channel=Nightly", "ide-plugin.channel=$newChannel")
        writeText(updated)
    }

    gradlew(
        "--info",
        "--stacktrace",
        "--quiet",
        "publishPlugin",
    )
}

data class ChannelAndVersion(val channel: String, val version: String) {
    companion object {
        fun from(api: ScriptApi): ChannelAndVersion {
            val channel = api.parameters["channel"]?.takeIf { it.isNotBlank() } ?: "Nightly"
            val version = "${api.executionNumber()}-${channel.uppercase()}"
            return ChannelAndVersion(channel, version)
        }
    }
}