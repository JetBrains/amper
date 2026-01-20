/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types.maven

import org.jetbrains.amper.frontend.plugins.AmperMavenPluginDescription
import org.jetbrains.amper.frontend.plugins.AmperMavenPluginMojo
import org.jetbrains.amper.maven.MavenPluginXml
import org.jetbrains.amper.maven.Mojo

class MavenPluginDescriptionAdapter(private val xml: MavenPluginXml) : AmperMavenPluginDescription {
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

fun MavenPluginXml.toAmperDescription(): AmperMavenPluginDescription = MavenPluginDescriptionAdapter(this)