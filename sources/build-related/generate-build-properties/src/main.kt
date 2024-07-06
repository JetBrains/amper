@file:Suppress("ReplacePrintlnWithLogging")

import org.eclipse.jgit.internal.storage.file.FileRepository
import org.yaml.snakeyaml.Yaml
import java.io.StringWriter
import java.nio.file.Path
import java.util.Properties
import kotlin.collections.component1
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.use
import kotlin.use

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

fun main(args: Array<String>) {
    val (taskOutputDirectoryString) = args
    val taskOutputDirectory = Path.of(taskOutputDirectoryString)
        .also { it.createDirectories() }

    val projectRoot = Path.of(System.getProperty("user.dir")).parent.parent
    val commonModuleTemplate = projectRoot.resolve("sources/common.module-template.yaml")
    check(commonModuleTemplate.exists()) {
        "Common module template file doesn't exist: $commonModuleTemplate"
    }
    val gitRoot = projectRoot.resolve(".git")

    val properties = Properties()
    properties["version"] = getCurrentVersion(commonModuleTemplate)

    if (gitRoot.isDirectory()) {
        val git = FileRepository(gitRoot.toFile())
        val head = git.getReflogReader("HEAD").lastEntry
        val shortHash = git.newObjectReader().use { it.abbreviate(head.newId).name() }
        properties["commitHash"] = head.newId.name
        properties["commitShortHash"] = shortHash
        properties["commitDate"] = head.who.`when`.toInstant().toString()
    } else {
        println("Git root directory doesn't exist: $gitRoot => skipping commit hash and date properties")
    }

    val propertiesString = StringWriter().use {
        properties.store(it, null)
        it
    }.toString().lines()
        .map { it.trim() }
        // remove datetime
        .filter { it.isNotBlank() && !it.startsWith("#")}
        .sorted()
        .map { it + "\n" }
        .joinToString("")

    writeContentIfChanged(taskOutputDirectory.resolve("build.properties"), propertiesString.toByteArray())
}

@Suppress("UNCHECKED_CAST")
private fun getCurrentVersion(commonModuleTemplate: Path): String {
    val commonTemplate = commonModuleTemplate.inputStream().use { Yaml().load<Map<String, Any>>(it) }
    val yamlSettings = commonTemplate.getValue("settings") as Map<String, Any>
    val yamlPublishing = yamlSettings.getValue("publishing") as Map<String, String>
    return yamlPublishing.getValue("version")
}

private fun writeContentIfChanged(file: Path, content: ByteArray) {
    if (file.exists()) {
        val existingContent = file.readBytes()
        if (content.contentEquals(existingContent)) return
    }

    file.writeBytes(content)
}
