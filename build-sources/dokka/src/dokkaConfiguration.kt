/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.dokka

import kotlinx.serialization.Serializable
import org.jetbrains.amper.plugins.EnumValue

@Serializable
internal data class DokkaConfiguration(
    val moduleName: String,
    val outputDir: String,
    val sourceSets: List<DokkaSourceSet>,
    val pluginsClasspath: List<String>,
    val pluginsConfiguration: List<PluginConfiguration>,
)

enum class DocumentedVisibility {
    @EnumValue("public")
    PUBLIC,
    @EnumValue("private")
    PRIVATE,
    @EnumValue("protected")
    PROTECTED,
    @EnumValue("internal")
    INTERNAL,
    @EnumValue("javaPackagePrivate")
    PACKAGE,
}

@Serializable
internal data class SourceSetID(
    val scopeId: String,
    val sourceSetName: String,
)

@Serializable
internal data class DokkaSourceSet(
    val sourceSetID: SourceSetID,
    val displayName: String,
    val classpath: List<String>,
    val sourceRoots: List<String>,
    val jdkVersion: Int,
    val documentedVisibilities: List<DocumentedVisibility>? = null,
    val analysisPlatform: String,
)

@Serializable
internal data class PluginConfiguration(
    val fqPluginName: String,
    val serializationFormat: String,
    val values: String,
)

@Serializable
internal data class DokkaBase(
    val customAssets: List<String>? = null,
    val customStyleSheets: List<String>? = null,
    val templatesDir: String? = null,
    val footerMessage: String? = null,
)