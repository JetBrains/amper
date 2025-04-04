/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.intellij.util.io.sha256Hex
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

plugins {
    `maven-publish`
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    jvm {
        mainRun {
            if (project.hasProperty("amper.cli.args")) {
                val args = project.property("amper.cli.args")
                args((args as String).split(" "))
            }
        }
    }
}

val junitListenersConfiguration = configurations.register("junitListenersClasspath")

dependencies {
    @Suppress("UnstableApiUsage")
    junitListenersConfiguration(project(":sources:junit-listeners"))
}

val unpackedDistribution by tasks.creating(Sync::class) {
    inputs.property("up-to-date", "11")

    fun CopySpec.toLib() = eachFile {
        relativePath = RelativePath(true, "lib", relativePath.lastName)
    }

    from(configurations.getByName("jvmRuntimeClasspath")) {
        toLib()
    }

    from(tasks.named("jvmJar")) {
        toLib()
    }

    includeEmptyDirs = false

    destinationDir = file("build/unpackedDistribution")
}

val listJUnitJars by tasks.registering {
    dependsOn(junitListenersConfiguration)
    val junitLauncherJarsFile = layout.buildDirectory.file("classpath.txt")
    outputs.file(junitLauncherJarsFile)
    doLast {
        val jars = junitListenersConfiguration.get().files.joinToString("\n") { it.name }
        junitLauncherJarsFile.get().asFile.writeText(jars)
    }
}

tasks.named<ProcessResources>("jvmProcessResources") {
    from(listJUnitJars) {
        into("junit-listeners")
    }
    from(junitListenersConfiguration) {
        into("junit-listeners")
    }
}

val prepareForLocalRun by tasks.creating {
    dependsOn(unpackedDistribution)
    dependsOn(":sources:android-integration:gradle-plugin:publishToMavenLocal")
}

val tgzDistribution by tasks.creating(Tar::class) {
    dependsOn(unpackedDistribution)

    from(unpackedDistribution.destinationDir)
    archiveClassifier = "dist"
    compression = Compression.GZIP
}

abstract class ProcessAmperScriptTask : DefaultTask() {
    @get:Incremental
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Incremental
    @get:InputFile
    abstract val amperDistTgzFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val amperDistVersion: Property<String>

    @get:Input
    abstract val outputWindowsLineEndings: Property<Boolean>

    @Suppress("SameParameterValue")
    private fun substituteTemplatePlaceholders(
        inputFile: Path,
        outputFile: Path,
        placeholder: String,
        values: List<Pair<String, String>>,
        outputWindowsLineEndings: Boolean = false,
    ) {
        var result = inputFile.readText().replace("\r", "")

        val missingPlaceholders = mutableListOf<String>()
        for ((name, value) in values) {
            check (!name.contains(placeholder)) {
                "Do not use placeholder '$placeholder' in name: $name"
            }

            val s = "$placeholder$name$placeholder"
            if (!result.contains(s)) {
                missingPlaceholders.add(s)
            }

            result = result.replace(s, value)
        }

        missingPlaceholders.forEach {
            logger.warn("Placeholder '$it' is not used in $outputFile")
        }

        result = result.replace05ExitCommandPadding()

        result = result
            .split('\n')
            .joinToString(if (outputWindowsLineEndings) "\r\n" else "\n")

        val escapedPlaceHolder = Pattern.quote(placeholder)
        val regex = Regex("$escapedPlaceHolder\\S+$escapedPlaceHolder")
        val unsubstituted = result
            .splitToSequence('\n')
            .mapIndexed { line, s -> "line ${line + 1}: $s" }
            .filter(regex::containsMatchIn)
            .joinToString("\n")
        check(unsubstituted.isBlank()) {
            "Some template parameters were left unsubstituted in template file $inputFile:\n$unsubstituted"
        }

        if (outputWindowsLineEndings) {
            check(result.count { it == '\r' } > 10) {
                "Windows line endings must be in the result after substituting for $inputFile"
            }
        }

        outputFile.parent.createDirectories()
        outputFile.writeText(result)
    }

    /**
     * See comment in amper.template.bat around the placeholder.
     */
    private val exitCommandPaddingPlaceholder = "@EXIT_COMMAND_PADDING@"
    private val exitCommandTargetOffset = 6826

    /**
     * See comment in amper.template.bat around the placeholder.
     */
    private fun String.replace05ExitCommandPadding(): String {
        val index = indexOf(exitCommandPaddingPlaceholder)
        if (index < 0) return this

        val paddingToTargetOffset = exitCommandTargetOffset - index
        check(paddingToTargetOffset > 0) {
            "Cannot add padding to reach target offset $exitCommandTargetOffset, the placeholder is already at $index. " +
                    "Please move the exit command from the comment further up the wrapper template"
        }
        return replace(exitCommandPaddingPlaceholder, " ".repeat(paddingToTargetOffset))
    }

    @TaskAction
    fun processScript() {
        substituteTemplatePlaceholders(
            inputFile = inputFile.get().asFile.toPath(),
            outputFile = outputFile.get().asFile.toPath(),
            placeholder = "@",
            values = listOf(
                "AMPER_VERSION" to amperDistVersion.get(),
                "AMPER_DIST_TGZ_SHA256" to sha256Hex(amperDistTgzFile.get().asFile.toPath()),
            ),
            outputWindowsLineEndings = outputWindowsLineEndings.get(),
        )
    }
}

val amperShellScript = tasks.register<ProcessAmperScriptTask>("amperShellScript") {
    inputFile = projectDir.resolve("resources/wrappers/amper.template.sh")
    outputFile = projectDir.resolve("build/amper")

    amperDistVersion = project.version.toString()
    amperDistTgzFile = tgzDistribution.archiveFile

    outputWindowsLineEndings = false
}

val amperBatScript = tasks.register<ProcessAmperScriptTask>("amperBatScript") {
    inputFile = projectDir.resolve("resources/wrappers/amper.template.bat")
    outputFile = projectDir.resolve("build/amper.bat")

    amperDistVersion = project.version.toString()
    amperDistTgzFile = tgzDistribution.archiveFile

    outputWindowsLineEndings = true
}

configurations.create("dist")

val distTgzArtifact = artifacts.add("dist", tgzDistribution.archiveFile) {
    type = "tgz"
    classifier = "dist"
    builtBy(tgzDistribution)
}

val shellScriptArtifact = artifacts.add("dist", amperShellScript.get().outputFile) {
    type = "sh"
    classifier = "wrapper"
}

val batScriptArtifact = artifacts.add("dist", amperBatScript.get().outputFile) {
    type = "bat"
    classifier = "wrapper"
}

publishing {
    publications.getByName<MavenPublication>("kotlinMultiplatform") {
        artifact(distTgzArtifact)
        artifact(shellScriptArtifact)
        artifact(batScriptArtifact)
    }
}
