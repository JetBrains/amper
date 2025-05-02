/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.templates

import io.github.classgraph.ClassGraph
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

/**
 * Accessor for Amper project templates.
 */
object AmperProjectTemplates {

    /**
     * All available Amper project templates.
     */
    val availableTemplates by lazy {
        javaClass.getResource("/templates/list.txt")
            ?.readText()
            ?.trim()
            ?.lines()
            ?.map { line ->
                val (name, description) = line.split(":").map { it.trim() }
                AmperProjectTemplate(name, description)
            }
            ?: error("Template list not found")
    }
}

/**
 * A predefined Amper project template, identified by [name].
 */
data class AmperProjectTemplate(val name: String, val description: String) {

    /**
     * Finds all files for this template in resources.
     */
    fun listFiles(): List<TemplateFile> = ClassGraph()
        .acceptJars("amper-project-templates-*.jar") // for perf, avoid scanning everything
        .acceptPaths("templates/$name")
        .scan()
        .use { scanResult ->
            scanResult.allResources.map { resource ->
                TemplateFile(resource.url, resource.path.removePrefix("templates/$name/"))
            }
        }
        .also {
            // something is very wrong (and out of the user's control) if there are no files
            check(it.isNotEmpty()) { "No files were found for template '$name'" }
        }
}

/**
 * A file from an Amper project template.
 */
data class TemplateFile(
    /**
     * The URL to the resource for this file.
     */
    val resourceUrl: URL, // will be used in IDE wizard
    /**
     * The path to this file relative to the project root.
     */
    val relativePath: String,
) {
    /**
     * Extracts this file from the resources to the given [projectRoot].
     *
     * If the file already exists in the project root, it will be overwritten.
     */
    fun extractTo(projectRoot: Path) {
        val path = projectRoot.resolve(relativePath)
        path.parent.createDirectories()
        resourceUrl.openStream().use { stream ->
            path.outputStream().use { out ->
                stream.copyTo(out)
            }
        }
    }
}
