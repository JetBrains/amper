/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("plugin")
data class MavenPluginXml(
    @XmlElement val name: String,
    @XmlElement val description: String,
    @XmlElement val groupId: String,
    @XmlElement val artifactId: String,
    @XmlElement val version: String,
    @XmlElement val goalPrefix: String,
    @XmlElement val isolatedRealm: Boolean,
    @XmlElement val inheritedByDefault: Boolean,
    @XmlElement val requiredJavaVersion: String?,
    @XmlElement val requiredMavenVersion: String?,
    @XmlElement val mojos: Mojos,
    @XmlElement val dependencies: Dependencies,
)

@Serializable
@XmlSerialName("mojos")
data class Mojos(val mojos: List<Mojo>) : List<Mojo> by mojos

@Serializable
@XmlSerialName("dependencies")
data class Dependencies(val dependencies: List<Dependency>) : List<Dependency> by dependencies

@Serializable
@XmlSerialName("mojo")
data class Mojo(
    @XmlElement val goal: String,
    @XmlElement val phase: String?,
    @XmlElement val requiresDependencyResolution: String?,
    @XmlElement val description: String?,
    @XmlElement val requiresDirectInvocation: Boolean,
    @XmlElement val requiresProject: Boolean,
    @XmlElement val requiresReports: Boolean,
    @XmlElement val aggregator: Boolean,
    @XmlElement val requiresOnline: Boolean,
    @XmlElement val inheritedByDefault: Boolean,
    @XmlElement val implementation: String,
    @XmlElement val language: String,
    @XmlElement val instantiationStrategy: String,
    @XmlElement val executionStrategy: String,
    @XmlElement val threadSafe: Boolean,
    @XmlElement val parameters: Parameters,
    @XmlElement val configuration: Configuration,
)

@Serializable
@XmlSerialName("configuration")
data class Configuration(val parameterValues: List<ParameterValue>) : List<ParameterValue> by parameterValues

@Serializable
data class ParameterValue(
    val implementation: String? = null,
    @XmlSerialName("default-value") val defaultValue: String? = null,
    // This is set by the custom XML reader, since the default one does not support dynamic tags.
    @Transient val parameterName: String = "",
)

@Serializable
@XmlSerialName("parameters")
data class Parameters(val parameters: List<Parameter>) : List<Parameter> by parameters

@Serializable
@XmlSerialName("parameter")
data class Parameter(
    @XmlElement val name: String,
    @XmlElement val type: String,
    @XmlElement val required: Boolean,
    @XmlElement val editable: Boolean,
    @XmlElement val description: String,
)

@Serializable
@XmlSerialName("dependency")
data class Dependency(
    @XmlElement val groupId: String,
    @XmlElement val artifactId: String,
    @XmlElement val version: String,
    @XmlElement val type: String,
)