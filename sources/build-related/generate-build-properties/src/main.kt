@file:Suppress("ReplacePrintlnWithLogging")

import org.eclipse.jgit.api.Git
import org.yaml.snakeyaml.Yaml
import java.io.StringWriter
import java.nio.file.Path
import java.util.Properties
import kotlin.collections.component1
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.use

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

fun main(args: Array<String>) {
    val (taskOutputDirectoryString) = args
    val taskOutputDirectory = Path(taskOutputDirectoryString).createDirectories()

    val projectRoot = Path(System.getProperty("user.dir")).parent.parent
    val commonModuleTemplate = projectRoot.resolve("sources/common.module-template.yaml")
    check(commonModuleTemplate.exists()) {
        "Common module template file doesn't exist: $commonModuleTemplate"
    }
    val gitRoot = projectRoot.resolve(".git")

    val properties = Properties()
    properties["version"] = getCurrentVersion(commonModuleTemplate)

    if (gitRoot.isDirectory()) {
        // This is to avoid issues with people who use config parameters that are not supported by JGit.
        // For example, the 'patience' diff algorithm isn't supported.
        runWithoutGlobalGitConfig {
            Git.open(gitRoot.toFile()).use { git ->
                val repo = git.repository
                val head = repo.getReflogReader("HEAD").lastEntry
                val shortHash = repo.newObjectReader().use { it.abbreviate(head.newId).name() }
                properties["commitHash"] = head.newId.name
                properties["commitShortHash"] = shortHash
                properties["commitDate"] = head.who.whenAsInstant.toString()

                // When developing locally, we want to somehow capture changes to the local sources because we want to
                // invalidate incremental state files based on this. If we don't, changing some Amper code will not
                // cause state invalidation, and some tasks will be marked up-to-date even though their code has changed
                // and they would produce a different output.
                // Using the git index for this is insufficient because it only captures the paths but not the contents
                // of the files. That's why we use a digest of the whole diff.
                properties["localChangesHash"] = git.localChangesHash()
            }
        }
    } else {
        println("Git root directory doesn't exist: $gitRoot => skipping commit hash and date properties")
    }

    val propertiesString = StringWriter().use {
        properties.store(it, null)
        it
    }.toString().lines()
        .map { it.trim() }
        // remove datetime
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .sorted()
        .joinToString("\n")

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
