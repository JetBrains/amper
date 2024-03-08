/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.intellij.util.io.sha256Hex

plugins {
    `maven-publish`
    id("org.jetbrains.kotlin.plugin.atomicfu") version "1.9.22"
}

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
    abstract val addCR: Property<Boolean>

    @TaskAction
    fun processScript() {
        val amperVersion = amperDistVersion.get()
        val amperSha256 = sha256Hex(amperDistFile.get().asFile.toPath())

        val text = inputFile.get().asFile.readText()

        if (amperVersion.contains("-dev-")) {
            require(!text.contains(amperVersion)) {
                "Script '${inputFile.get().asFile}' text should not contain $amperVersion:\n$text"
            }

            require(!text.contains(amperSha256)) {
                "Script '${inputFile.get().asFile}' text should not contain $amperSha256:\n$text"
            }
        }

        val newText = text
            .split('\n')
            .map { it.trimEnd('\r') }
            .map { line -> line.replace(Regex("^((set )?amper_version=).*\$"), "$1$amperVersion") }
            .map { line -> line.replace(Regex("^((set )?amper_sha256=).*\$"), "$1$amperSha256") }
            .map { if (addCR.get()) "$it\r" else it }
            .joinToString("\n")

        check(newText.contains(amperVersion)) {
            "Script text must contain $amperVersion after replacement:\n$newText"
        }

        check(newText.contains(amperSha256)) {
            "Script text must contain $amperSha256 after replacement:\n$newText"
        }

        outputFile.get().asFile.writeText(newText)
    }
}

val amperShellScript = tasks.register<ProcessAmperScriptTask>("amperShellScript") {
    inputFile = projectDir.resolve("scripts/amper.sh")
    outputFile = projectDir.resolve("build/amper.sh")

    amperDistVersion = project.version.toString()
    amperDistFile = amperDist

    addCR = false
}

val amperBatScript = tasks.register<ProcessAmperScriptTask>("amperBatScript") {
    inputFile = projectDir.resolve("scripts/amper.bat")
    outputFile = projectDir.resolve("build/amper.bat")

    amperDistVersion = project.version.toString()
    amperDistFile = amperDist

    addCR = true
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
