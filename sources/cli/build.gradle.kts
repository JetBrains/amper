/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.intellij.util.io.sha256Hex
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.createDirectories

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

val zipDistribution by tasks.creating(Zip::class) {
    dependsOn(unpackedDistribution)

    from(unpackedDistribution.destinationDir)
    archiveClassifier = "dist"
}

val amperDist: Provider<RegularFile> = zipDistribution.archiveFile

abstract class ProcessAmperScriptTask : DefaultTask() {
    @get:Incremental
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Incremental
    @get:InputFile
    abstract val amperDistFile: RegularFileProperty

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
        var result = Files.readString(inputFile)
            .replace("\r", "")

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

        check(missingPlaceholders.isEmpty()) {
            "Missing placeholders [${missingPlaceholders.joinToString(" ")}] in template file $inputFile"
        }

        result = result
            .split('\n')
            .joinToString(if (outputWindowsLineEndings) "\r\n" else "\n")

        val escapedPlaceHolder = Pattern.quote(placeholder)
        val regex = Regex("$escapedPlaceHolder.+$escapedPlaceHolder")
        val unsubstituted = result
            .splitToSequence('\n')
            .mapIndexed { line, s -> "line ${line + 1}: $s" }
            .filter(regex::containsMatchIn)
            .joinToString("\n")
        check (unsubstituted.isBlank()) {
            "Some template parameters were left unsubstituted in template file $inputFile:\n$unsubstituted"
        }

        if (outputWindowsLineEndings) {
            check(result.count { it == '\r' } > 10) {
                "Windows line endings must be in the result after substituting for $inputFile"
            }
        }

        outputFile.parent.createDirectories()
        Files.writeString(outputFile, result)
    }

    @TaskAction
    fun processScript() {
        substituteTemplatePlaceholders(
            inputFile = inputFile.get().asFile.toPath(),
            outputFile = outputFile.get().asFile.toPath(),
            placeholder = "@",
            values = listOf(
                "AMPER_VERSION" to amperDistVersion.get(),
                "AMPER_DIST_SHA256" to sha256Hex(amperDistFile.get().asFile.toPath()),
            ),
            outputWindowsLineEndings = outputWindowsLineEndings.get(),
        )
    }
}

val amperShellScript = tasks.register<ProcessAmperScriptTask>("amperShellScript") {
    inputFile = projectDir.resolve("resources/wrappers/amper.template.sh")
    outputFile = projectDir.resolve("build/amper")

    amperDistVersion = project.version.toString()
    amperDistFile = amperDist

    outputWindowsLineEndings = false
}

val amperBatScript = tasks.register<ProcessAmperScriptTask>("amperBatScript") {
    inputFile = projectDir.resolve("resources/wrappers/amper.template.bat")
    outputFile = projectDir.resolve("build/amper.bat")

    amperDistVersion = project.version.toString()
    amperDistFile = amperDist

    outputWindowsLineEndings = true
}

configurations.create("dist")

val distArtifact = artifacts.add("dist", amperDist) {
    type = "zip"
    classifier = "dist"
    builtBy(zipDistribution)
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
        artifact(distArtifact)
        artifact(shellScriptArtifact)
        artifact(batScriptArtifact)
    }
}
