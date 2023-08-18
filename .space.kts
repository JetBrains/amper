@file:DependsOn("com.slack.api:slack-api-client:1.30.0")

import circlet.pipelines.script.ScriptApi
import com.slack.api.Slack
import java.io.File
import java.util.*
import kotlin.script.experimental.dependencies.DependsOn
import kotlin.system.exitProcess

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
    parameters {
        secret("slack_secret_space_alerts_app", value = "{{ project:slack_secret_space_alerts_app }}")
        customParameters()
    }
    container(displayName = name, image = "thyrlian/android-sdk") {
        workDir = "prototype-implementation"
        env["SLACK_TOKEN"] = "{{ slack_secret_space_alerts_app }}"
        customContainerBody()
        kotlinScript {
            it.addCreds()
            try {
                it.scriptBody()
            }
            catch (ex: Exception) {
                println("Sending notification to slack")
                val slack = Slack.getInstance()
                val token = System.getenv("SLACK_TOKEN")
                val branchName = System.getenv("JB_SPACE_GIT_BRANCH").substringAfterLast("/")

                slack.methods(token).chatPostMessage { req ->
                    req.channel("#deft-build-alerts").text("<${it.executionUrl()}|${name} `#${it.executionNumber()}`> are failed in branch `${branchName}`")
                }
                exitProcess(1)
            }
        }
    }
}

// Common build for every push.
registerJobInPrototypeDir("Build") {
    gradlew(
        "--info",
        "--stacktrace",
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
            val version = "${api.executionNumber()}-${channel.uppercase(Locale.getDefault())}"
            return ChannelAndVersion(channel, version)
        }
    }
}