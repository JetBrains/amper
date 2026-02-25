/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.Plugin
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.utils.cli.CommandLineUtils
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.jetbrains.amper.frontend.tree.ObjectBuilderContext
import org.jetbrains.amper.frontend.tree.TypeDescriptor
import org.jetbrains.amper.frontend.tree.ValueSinkPoint
import org.jetbrains.amper.frontend.tree.add
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.tree.put
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.generated.*
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
        settings {
            jvm {
                test {
                    contributeTestSettings(config)
                }
            }
        }
    }

    addUnsupportedParameterComments(config)
}

private fun ObjectBuilderContext<DeclarationOfJvmTestSettings>.contributeTestSettings(
    config: Xpp3Dom,
) {
    config.children.forEach { child ->
        when (child.name) {
            "argLine" -> {
                child.value?.let { argLineValue ->
                    // Unfortunately, the Maven surefire plugin docs don't specify the contract for the argLine parameter.
                    // In order to respect the contract, we had to look at the implementation, and this function is
                    // exactly what that plugin calls, so we use the same function here to match their behavior.
                    val args = CommandLineUtils.translateCommandline(argLineValue)
                    if (args.isNotEmpty()) {
                        freeJvmArgs {
                            args.forEach { arg -> add(arg) }
                        }
                    }
                }
            }
            "environmentVariables" -> {
                handleMap(child, extraEnvironment)
            }
            "systemPropertyVariables" -> {
                handleMap(child, systemProperties)
            }
        }
    }
}

context(_: ObjectBuilderContext<*>)
private fun handleMap(
    child: Xpp3Dom,
    targetProperty: ValueSinkPoint<TypeDescriptor.Map<TypeDescriptor.String>, SchemaObjectDeclaration.Property>,
) {
    val envVars = child.children.filterIsInstance<Xpp3Dom>()
        .filter { it.value != null }
        .associate { it.name to it.value }
    if (envVars.isNotEmpty()) {
        targetProperty {
            envVars.forEach { (key, value) ->
                put[key](value)
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
