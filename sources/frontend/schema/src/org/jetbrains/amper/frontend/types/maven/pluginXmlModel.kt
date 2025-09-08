/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types.maven

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.jetbrains.amper.frontend.plugins.Dependency
import org.jetbrains.amper.frontend.plugins.MavenPluginXml
import org.jetbrains.amper.frontend.plugins.Mojo
import org.jetbrains.amper.frontend.plugins.Parameter

@Serializable
@XmlSerialName("plugin")
data class DefaultMavenPluginXml(
    @XmlElement override val name: String,
    @XmlElement override val description: String,
    @XmlElement override val groupId: String,
    @XmlElement override val artifactId: String,
    @XmlElement override val version: String,
    @XmlElement override val goalPrefix: String,
    @XmlElement override val isolatedRealm: Boolean,
    @XmlElement override val inheritedByDefault: Boolean,
    @XmlElement override val requiredJavaVersion: String,
    @XmlElement override val requiredMavenVersion: String,
    @XmlElement override val mojos: DefaultMojos,
    @XmlElement override val dependencies: DefaultDependencies,
) : MavenPluginXml

@Serializable
@XmlSerialName("mojos")
data class DefaultMojos(val mojos: List<DefaultMojo>) : List<Mojo> by mojos

@Serializable
@XmlSerialName("dependencies")
data class DefaultDependencies(val dependencies: List<Dependency>) : List<Dependency> by dependencies

@Serializable
@XmlSerialName("mojo")
data class DefaultMojo(
    @XmlElement override val goal: String,
    @XmlElement override val phase: String?,
    @XmlElement override val description: String,
    @XmlElement override val requiresDirectInvocation: Boolean,
    @XmlElement override val requiresProject: Boolean,
    @XmlElement override val requiresReports: Boolean,
    @XmlElement override val aggregator: Boolean,
    @XmlElement override val requiresOnline: Boolean,
    @XmlElement override val inheritedByDefault: Boolean,
    @XmlElement override val implementation: String,
    @XmlElement override val language: String,
    @XmlElement override val instantiationStrategy: String,
    @XmlElement override val executionStrategy: String,
    @XmlElement override val threadSafe: Boolean,
    @XmlElement override val parameters: DefaultParameters,
) : Mojo

@Serializable
@XmlSerialName("parameters")
data class DefaultParameters(val parameters: List<DefaultParameter>) : List<Parameter> by parameters

@Serializable
@XmlSerialName("parameter")
data class DefaultParameter(
    @XmlElement override val name: String,
    @XmlElement override val type: String,
    @XmlElement override val required: Boolean,
    @XmlElement override val editable: Boolean,
    @XmlElement override val description: String,
) : Parameter

@Serializable
@XmlSerialName("dependency")
data class DefaultDependency(
    @XmlElement override val groupId: String,
    @XmlElement override val artifactId: String,
    @XmlElement override val version: String,
    @XmlElement override val type: String,
) : Dependency