/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.Plugin
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.utils.cli.CommandLineUtils
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.JvmTestSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.tree.SyntheticBuilder
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.maven.MavenConverterBundle
import org.jetbrains.amper.maven.ProjectTreeBuilder
import org.jetbrains.amper.maven.YamlComment
import kotlin.io.path.div

private val supportedParameters = setOf("argLine", "environmentVariables", "systemPropertyVariables")

internal fun ProjectTreeBuilder.contributeSurefirePlugin(reactorProjects: Set<MavenProject>) {
    for (project in reactorProjects.filterJarProjects()) {
        module(project.basedir.toPath() / "module.yaml") {
            val plugin = project.getEffectivePlugin("org.apache.maven.plugins", "maven-surefire-plugin")
            if (plugin != null) {
                contributeSurefirePlugin(plugin)
            }
        }
    }
}

private fun ProjectTreeBuilder.ModuleTreeBuilder.contributeSurefirePlugin(plugin: Plugin) {
    val config = plugin.configuration as? Xpp3Dom ?: return

    withTestContext {
        `object`<Module> {
            Module::settings {
                Settings::jvm {
                    JvmSettings::test {
                        contributeTestSettings(this@withTestContext, config)
                    }
                }
            }
        }
    }

    addUnsupportedParameterComments(config)
}

context(mapLikeValueBuilder: SyntheticBuilder.MapLikeValueBuilder)
private fun contributeTestSettings(
    syntheticBuilder: SyntheticBuilder,
    config: Xpp3Dom,
) {
    with(mapLikeValueBuilder) {
        config.children.forEach { child ->
            when (child.name) {
                "argLine" -> {
                    handleList(child, syntheticBuilder, JvmTestSettings::freeJvmArgs)
                }
                "environmentVariables" -> {
                    handleMap(child, syntheticBuilder, JvmTestSettings::extraEnvironment)
                }
                "systemPropertyVariables" -> {
                    handleMap(child, syntheticBuilder, JvmTestSettings::systemProperties)
                }
            }
        }
    }
}

private fun SyntheticBuilder.MapLikeValueBuilder.handleList(
    child: Xpp3Dom,
    syntheticBuilder: SyntheticBuilder,
    targetProperty: kotlin.reflect.KProperty1<JvmTestSettings, *>,
) {
    child.value?.let { argLineValue ->
        // Unfortunately, the Maven surefire plugin docs don't specify the contract for the argLine parameter.
        // In order to respect the contract, we had to look at the implementation, and this function is
        // exactly what that plugin calls, so we use the same function here to match their behavior.
        val args = CommandLineUtils.translateCommandline(argLineValue)
        if (args.isNotEmpty()) {
            targetProperty setTo syntheticBuilder.list(
                SchemaType.ListType(SchemaType.StringType)
            ) {
                args.forEach { arg ->
                    add(syntheticBuilder.scalar(arg))
                }
            }
        }
    }
}

private fun SyntheticBuilder.MapLikeValueBuilder.handleMap(
    child: Xpp3Dom,
    syntheticBuilder: SyntheticBuilder,
    targetProperty: kotlin.reflect.KProperty1<JvmTestSettings, *>,
) {
    val envVars = child.children.filterIsInstance<Xpp3Dom>()
        .filter { it.value != null }
        .associate { it.name to it.value }
    if (envVars.isNotEmpty()) {
        targetProperty setTo syntheticBuilder.map(
            SchemaType.MapType(SchemaType.StringType, SchemaType.StringType)
        ) {
            envVars.forEach { (key, value) ->
                key setTo syntheticBuilder.scalar(value)
            }
        }
    }
}

private fun ProjectTreeBuilder.ModuleTreeBuilder.addUnsupportedParameterComments(config: Xpp3Dom) {
    val unsupportedChildren = config.children.filterIsInstance<Xpp3Dom>()
        .filter { it.name !in supportedParameters }

    if (unsupportedChildren.isEmpty()) return

    val warnComment = buildString {
        unsupportedChildren.forEach { child ->
            appendLine(MavenConverterBundle.message("unsupported.configuration"))
            appendLine(child.toString()
                .removePrefix("""<?xml version="1.0" encoding="UTF-8"?>""")
                .trim())
        }
    }.trim()

    addYamlComment(
        YamlComment(
            path = listOf("settings", "jvm", "test"),
            afterValueComment = warnComment,
            test = true,
        )
    )
}
