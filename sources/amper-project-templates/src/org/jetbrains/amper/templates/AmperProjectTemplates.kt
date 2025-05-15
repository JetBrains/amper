/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.templates

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.amper.templates.json.TemplateDescriptor
import org.jetbrains.amper.templates.json.TemplateResource
import org.jetbrains.annotations.Nls
import java.net.URL
import java.nio.file.Path
import java.util.ResourceBundle
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

/**
 * Accessor for Amper project templates.
 */
object AmperProjectTemplates {

    private val bundle = ResourceBundle.getBundle("messages.ProjectTemplatesBundle")

    /**
     * All available Amper project templates.
     */
    @OptIn(ExperimentalSerializationApi::class)
    val availableTemplates by lazy {
        javaClass.getResource("/templates/templates.json")
            ?.openStream()
            ?.use { Json.decodeFromStream<List<TemplateDescriptor>>(it) }
            ?.map { template ->
                AmperProjectTemplate(
                    id = template.id,
                    name = bundle.getString("template.${template.id}.name"),
                    description = bundle.getString("template.${template.id}.description"),
                    fileResources = template.resources,
                )
            }
            ?.sortedBy { it.name.lowercase() }
            ?: error("'templates.json' resource not found")
    }
}

/**
 * A predefined Amper project template, identified by [id].
 */
data class AmperProjectTemplate(
    val id: String,
    @Nls
    val name: String,
    @Nls
    val description: String,
    private val fileResources: List<TemplateResource>,
) {
    /**
     * Finds all files for this template in resources.
     */
    fun listFiles(): List<TemplateFile> = fileResources.map { res ->
        val resourceUrl = javaClass.getResource(res.name) ?: error("Missing resource in template '$id': $res")
        TemplateFile(resourceUrl = resourceUrl, relativePath = res.targetPath)
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
