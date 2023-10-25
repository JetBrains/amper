@file:DependsOn("com.slack.api:slack-api-client:1.30.0")

import circlet.pipelines.script.ScriptApi
import com.slack.api.Slack
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.name
import kotlin.script.experimental.dependencies.DependsOn
import kotlin.system.exitProcess

fun ScriptApi.addCreds() {
    File("local.properties").writeText(
        """
                scratch.username=${spaceClientId()}
                scratch.password=${spaceClientSecret()}
            """.trimIndent()
    )
}

fun registerJobInPrototypeDir(
    name: String,
    customTrigger: (Triggers.() -> Unit)? = null,
    customParameters: Parameters.() -> Unit = { },
    customContainerBody: Container.() -> Unit = { },
    hostJob: Host.() -> Unit = {},
    scriptBody: ScriptApi.() -> Unit,
) = job(name) {
    if (customTrigger != null) startOn { customTrigger() }
    parameters {
        secret("slack_secret_space_alerts_app", value = "{{ project:slack_secret_space_alerts_app }}")
        secret("git_ssh_key", "{{ project:deft-automation-bot-ssh-key }}")
        customParameters()
    }
    container(displayName = name, image = "registry.jetbrains.team/p/deft/containers/android-sdk:latest") {
        workDir = "prototype-implementation"
        env["SLACK_TOKEN"] = "{{ slack_secret_space_alerts_app }}"
        customContainerBody()

        // Add test report.
        fileArtifacts {
            localPath = "build/aggregatedTestReport.zip"
            optional = true
            onStatus = OnStatus.ALWAYS
            remotePath = "{{ run:number }}/aggregatedTestReport.zip"
        }

        kotlinScript {
            it.addCreds()
            try {
                it.scriptBody()
            } catch (ex: Exception) {
                println("Sending notification to slack")
                val slack = Slack.getInstance()
                val token = System.getenv("SLACK_TOKEN")
                val branchName = System.getenv("JB_SPACE_GIT_BRANCH").substringAfterLast("/")
                if (branchName == "main") {
                    slack.methods(token).chatPostMessage { req ->
                        req.channel("#deft-build-alerts")
                            .text("<${it.executionUrl()}|${name} `#${it.executionNumber()}`> are failed in branch `${branchName}`")
                    }
                }
                else if (branchName.contains("update-plugin")) {
                    slack.methods(token).chatPostMessage { req ->
                        req.channel("#deft-build-alerts")
                            .text("<${it.executionUrl()}|${name} `#${it.executionNumber()}`> are failed in branch `${branchName}`." +
                                    "\nTests failed after update version of gradle plugin to latest, please take a look.")
                    }
                }
                exitProcess(1)
            }
        }
    }

    host { hostJob() }
}

// Common build for every push.
registerJobInPrototypeDir("Build",
    customTrigger = { gitPush {
        anyBranchMatching {
            -"*update-plugin*"
        }
    }}) {
    gradlew(
        "--info",
        "--stacktrace",
        "--continue",
        "allTests",
        "aggregateTestReports"
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
    },
    hostJob = {
        env["GIT_SSH_KEY"] = "{{ git_ssh_key }}"
        var newVersion = ""

        kotlinScript("Update plugin version") {
            val channelAndVersion = ChannelAndVersion.from(it)
            newVersion = channelAndVersion.version

            val path = "~/pluginVersion.txt"
            val content = newVersion
            val sharedFile = File(path).apply {
                parentFile.mkdirs()
                createNewFile()
                writeText(content)
            }
            it.fileShare().put(sharedFile, "pluginVersion.txt")

            val syncFile = File("syncVersions.sh")
            val text = syncFile.readText()
            val currentVersion =
                Regex("AMPER_VERSION=\".*\"").find(text)!!.value.substringAfter("AMPER_VERSION=").replace("\"", "")
            println("OLD plugin version $currentVersion")
            val updatedText = text.replace(currentVersion, newVersion)
            syncFile.writeText(updatedText)

            Files.walk(Paths.get("")).use { stream ->
                stream.filter {
                    Files.isRegularFile(it) && (it.name == "settings.gradle.kts" || it.name.endsWith(".md"))
                }.forEach {
                    val file = File(it.toAbsolutePath().toString())
                    val fileText = file.readText()
                    val newText = fileText.replace(currentVersion, newVersion)
                    file.writeText(newText)
                }
            }
        }

        shellScript("Commit and push changes") {
            content = """
                mkdir -p ${'$'}JB_SPACE_WORK_DIR_PATH/.ssh
                    echo "${'$'}GIT_SSH_KEY" >> ${'$'}JB_SPACE_WORK_DIR_PATH/.ssh/id_rsa
                    chmod 400 ${'$'}JB_SPACE_WORK_DIR_PATH/.ssh/id_rsa

                    export GIT_SSH_COMMAND="ssh -i ${'$'}JB_SPACE_WORK_DIR_PATH/.ssh/id_rsa -o UserKnownHostsFile=/dev/null -F none -o IdentitiesOnly=yes -o StrictHostKeyChecking=no"

                    git config user.name "Space Automation"
                    git config user.email no-reply@automation.jetbrains.space
                    
                    python3 -m pip install tqdm requests

                    export BRANCH_NAME=${'$'}(date +update-plugin-%Y-%m-%d-%H-%M-%S)
                    
                    echo ${'$'}BRANCH_NAME > ${'$'}JB_SPACE_FILE_SHARE_PATH/branch.txt
                    
                    export PLUGIN_VERSION 
                    
                    echo PLUGIN_VERSION < ${'$'}JB_SPACE_FILE_SHARE_PATH/pluginVersion.txt

                    git checkout -b ${'$'}BRANCH_NAME

                    git add --update

                    git status
                    git commit -m "Update Amper plugin version
                    git push -u origin ${'$'}BRANCH_NAME
            """
        }

        kotlinScript("Create MR") { api ->
            val branchName = api.fileShare().locate("branch.txt")?.readText()?.trim('\n')
            val pluginVersion = api.fileShare().locate("pluginVersion.txt")?.readText()?.trim('\n')
            api.space().projects.codeReviews.createMergeRequest(
                project = ProjectIdentifier.Key("deft"),
                repository = "deft-prototype",
                sourceBranch = "$branchName",
                targetBranch = "main",
                title = "Update Amper plugin version to $pluginVersion"
            )
        }

    }
) {
    val channelAndVersion = ChannelAndVersion.from(this)
    println("Publishing with version: ${channelAndVersion.version}")

    channelAndVersion.writeTo(
        filePath = "common.module-template.yaml",
        versionPrefix = "version: "
    )

    // Do the work.
    gradlew(
        "--info",
        "--stacktrace",
        "allTests",
        "publishAllPublicationsToScratchRepository",
    )
}

// Cherry-pick generated commit with new plugin version if tests passed
registerJobInPrototypeDir(
    "Update gradle plugin version",
    customTrigger = { gitPush {
        anyBranchMatching {
            +"*update-plugin*"
        }
    }},
    hostJob = {
        kotlinScript("Merge changes") { api ->
            val branchName = api.gitBranch().substringAfterLast("/")
            println("BRANCH NAME $branchName")

            val syncFile = File("syncVersions.sh")
            val text = syncFile.readText()
            val newPluginVersion =
                Regex("AMPER_VERSION=\".*\"").find(text)!!.value.substringAfter("AMPER_VERSION=").replace("\"", "")

            val review = api.space().projects.codeReviews.getAllCodeReviews(
                project = ProjectIdentifier.Key("deft"),
                state = CodeReviewStateFilter.Opened, text = "Update Amper plugin version to $newPluginVersion"
            ).data[0]

            api.space().projects.codeReviews.mergeMergeRequest(
                project = ProjectIdentifier.Key(api.projectKey()),
                reviewId = ReviewIdentifier.Id(review.review.id),
                deleteSourceBranch = true,
                mergeMode = GitMergeMode.FF
            )
        }
    }) {
    gradlew(
        "--info",
        "--stacktrace",
        "allTests",
        "aggregateTestReports"
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