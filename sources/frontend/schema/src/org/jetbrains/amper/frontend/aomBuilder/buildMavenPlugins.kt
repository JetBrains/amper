/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.amper.frontend.plugins.MavenPluginXml
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.mavenPluginXmlsDir
import org.jetbrains.amper.frontend.types.maven.DefaultMavenPluginXml
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

internal val xml = XML {
    defaultPolicy { ignoreUnknownChildren() }
}

context(problemReporter: ProblemReporter)
fun AmperProjectContext.loadMavenPluginXmls(): List<MavenPluginXml> =
    mavenPluginXmlsDir.takeIf { it.isDirectory() }
        ?.listDirectoryEntries("*.xml").orEmpty()
        .mapNotNull { parseMavenPluginXml(it) }

context(problemReporter: ProblemReporter)
private fun parseMavenPluginXml(pluginXml: Path): MavenPluginXml? = try {
    xml.decodeFromString<DefaultMavenPluginXml>(pluginXml.readText())
} catch (e: SerializationException) {
    TODO("Report an error here")
}