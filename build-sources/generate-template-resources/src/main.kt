/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.jetbrains.amper.templates.json.TemplateDescriptor
import org.jetbrains.amper.templates.json.TemplateResource
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    check(args.size == 1) { "Expected 1 argument, 'taskOutputDir', but got: ${args.joinToString(" ")}" }
    val taskOutputDir = Path(args[0]).createDirectories()
    // FIXME use a proper task input for this, this relies on the working dir for custom tasks being the module root
    val moduleDir = Path(System.getProperty("user.dir"))

    val templateDirs = moduleDir.resolve("resources/templates").listDirectoryEntries()
    val descriptors = templateDirs.map { createTemplateDescriptor(it) }
    taskOutputDir.resolve("templates/templates.json")
        .createParentDirectories()
        .outputStream()
        .use { outStream -> Json.encodeToStream(descriptors, outStream) }
}

private fun createTemplateDescriptor(templateDir: Path): TemplateDescriptor {
    val templateId = templateDir.name
    val files = templateDir.listDescendantRelativePathStrings()
    return TemplateDescriptor(
        id = templateId,
        resources = files.map { TemplateResource("/templates/$templateId/$it", it) },
    )
}

private fun Path.listDescendantRelativePathStrings(): List<String> =
    walk().map { it.relativeTo(this).joinToString("/") }.toList()
