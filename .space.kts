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

fun registerJobInPrototypeDir(
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
registerJobInPrototypeDir("Build") {
    gradlew(
        "--info",
        "--stacktrace",
        "--fail-fast",
        "allTests"
    )
}

// Nightly build for auto publishing.
registerJobInPrototypeDir(
    "Build and publish",
    customTrigger = { schedule { cron("0 0 * * *") } },
    customParameters = {
        text("channel", value = "Nightly") {
            options("Stable", "Nightly")
        }
    }
) {
    val channelAndVersion = ChannelAndVersion.from(this)
    println("Publishing with version: ${channelAndVersion.version}")

    channelAndVersion.writeTo(
        filePath = "common.Pot-template.yaml",
        versionPrefix = "version: "
    )

    // Do the work.
    gradlew(
        "--info",
        "--stacktrace",
        "--quiet",
        "allTests",
        "--fail-fast",
        "publishAllPublicationsToScratchRepository",
    )
}

// Build for publishing plugin.
registerJobInPrototypeDir(
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
    val channelAndVersion = ChannelAndVersion.from(this)
    println("Publishing to channel: ${channelAndVersion.channel} with version: ${channelAndVersion.version}")
    
    channelAndVersion.writeTo(
        filePath = "ide-plugin-231-232/gradle.properties",
        channelPrefix = "ide-plugin.channel=",
        versionPrefix = "ide-plugin.version="
    )

    gradlew(
        "--info",
        "--stacktrace",
        "--quiet",
        "publishPlugin",
    )
}

data class ChannelAndVersion(val channel: String, val version: String) {
    fun writeTo(
        filePath: String,
        channelPrefix: String? = null,
        versionPrefix: String? = null
    ) {
        val file = File(filePath).absoluteFile
        if (!file.isFile) error("file not found: $file")

        var text = file.readText()
        if (text.isEmpty()) error("file is empty: $file")

        fun replace(old: String, new: String) {
            if (!text.contains(old)) error(
                "cannot replace text in file: $file\n" +
                        "  text to replace not found : $old"
            )

            text = text.replace(old, new)

            if (!text.contains(new)) error(
                "cannot replace text in file: $file\n" +
                        "  old: $old" +
                        "  new: $new"
            )
        }

        if (channelPrefix != null) {
            replace("${channelPrefix}Nightly", "${channelPrefix}${channel}")
        }
        if (versionPrefix != null) {
            replace("${versionPrefix}1.0-SNAPSHOT", "${versionPrefix}${version}")
        }

        file.writeText(text)
    }

    companion object {
        fun from(api: ScriptApi): ChannelAndVersion {
            val channel = api.parameters["channel"]?.takeIf { it.isNotBlank() } ?: "Nightly"
            val version = "${api.executionNumber()}-${channel.uppercase()}"
            return ChannelAndVersion(channel, version)
        }
    }
}