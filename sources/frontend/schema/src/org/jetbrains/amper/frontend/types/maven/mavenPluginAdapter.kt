/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types.maven

import org.jetbrains.amper.frontend.MavenCoordinates
import org.jetbrains.amper.frontend.aomBuilder.MavenPluginWithXml
import org.jetbrains.amper.frontend.aomBuilder.traceableString
import org.jetbrains.amper.frontend.plugins.AmperMavenPluginDescription
import org.jetbrains.amper.frontend.plugins.AmperMavenPluginMojo
import org.jetbrains.amper.frontend.schema.toMavenCoordinates
import org.jetbrains.amper.maven.MavenPluginXml
import org.jetbrains.amper.maven.Mojo

class MavenPluginDescriptionAdapter(
    private val xml: MavenPluginXml,
    override val dependencies: List<MavenCoordinates>,
) : AmperMavenPluginDescription {
    override val groupId: String get() = xml.groupId
    override val artifactId: String get() = xml.artifactId
    override val version: String get() = xml.version
    override val goalPrefix: String get() = xml.goalPrefix
    override val mojos: List<AmperMavenPluginMojo> get() = xml.mojos.map { MojoAdapter(it) }
}

class MojoAdapter(private val mojo: Mojo) : AmperMavenPluginMojo {
    override val goal: String get() = mojo.goal
    override val phase: String? get() = mojo.phase
    override val requiresDependencyResolution: String? get() = mojo.requiresDependencyResolution
}

fun MavenPluginWithXml.toAmperDescription(): AmperMavenPluginDescription = MavenPluginDescriptionAdapter(
    xml = second,
    dependencies = first.dependencies.orEmpty().map { 
        it::coordinates.traceableString().toMavenCoordinates()
    },
)