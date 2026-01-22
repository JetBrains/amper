/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.ConfigurationContainer
import org.apache.maven.model.Plugin
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.jetbrains.amper.frontend.schema.AllOpenSettings
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.LombokSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.NoArgSettings
import org.jetbrains.amper.frontend.schema.SerializationSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.tree.SyntheticBuilder
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.maven.ProjectTreeBuilder
import kotlin.io.path.div


internal fun ProjectTreeBuilder.contributeKotlinPlugin(reactorProjects: Set<MavenProject>) {
    for (project in reactorProjects.filterJarProjects()) {
        module(project.basedir.toPath() / "module.yaml") {
            project.model.build.plugins
                .filter { it.groupId == "org.jetbrains.kotlin" && it.artifactId == "kotlin-maven-plugin" }
                .forEach { plugin -> contributeKotlinPlugin(plugin) }
        }
    }
}

private fun ProjectTreeBuilder.ModuleTreeBuilder.contributeKotlinPlugin(
    plugin: Plugin,
) {
    // Kotlin plugin common settings
    withDefaultContext {
        `object`<Module> {
            Module::settings {
                Settings::kotlin {
                    KotlinSettings::version setTo scalar(plugin.version)
                }
            }
        }
    }

    // executions
    plugin.executions.forEach { execution ->
        when (execution.id) {
            "compile" -> {
                withDefaultContext {
                    `object`<Module> {
                        Module::settings { execution.configureKotlinExecution() }
                    }
                }
            }
            "test-compile" -> {
                withTestContext {
                    `object`<Module> {
                        Module::settings { execution.configureKotlinExecution() }
                    }
                }
            }
        }
    }

    // top-level configuration
    if (plugin.configuration != null) {
        withDefaultContext {
            `object`<Module> {
                Module::settings { plugin.configureKotlinExecution() }
            }
        }
    }
}

context(sb: SyntheticBuilder, mapLikeValueBuilder: SyntheticBuilder.MapLikeValueBuilder)
private fun ConfigurationContainer.configureKotlinExecution() {
    with(mapLikeValueBuilder) {
        val config = configuration
        if (config is Xpp3Dom) {
            config.children.forEach { child ->
                when (child.name) {
                    "args" -> {
                        val freeCompilerArgs = buildList {
                            child.children.forEach { arg ->
                                add(arg.value)
                            }
                        }
                        Settings::kotlin {
                            KotlinSettings::freeCompilerArgs setTo sb.list(
                                SchemaType.ListType(SchemaType.StringType)
                            ) {
                                freeCompilerArgs.forEach { arg ->
                                    add(sb.scalar(arg))
                                }
                            }
                        }
                    }
                    "compilerPlugins" -> {
                        child.children.forEach { plugin ->
                            when (plugin.value) {
                                "all-open" -> {
                                    Settings::kotlin {
                                        KotlinSettings::allOpen {
                                            AllOpenSettings::enabled setTo sb.scalar(true)
                                        }
                                    }
                                }
                                "no-arg" -> {
                                    Settings::kotlin {
                                        KotlinSettings::noArg {
                                            NoArgSettings::enabled setTo sb.scalar(true)
                                        }
                                    }
                                }
                                "lombok" -> {
                                    Settings::lombok {
                                        LombokSettings::enabled setTo sb.scalar(true)
                                    }
                                }
                                "kotlinx-serialization" -> {
                                    Settings::kotlin {
                                        KotlinSettings::serialization {
                                            SerializationSettings::enabled setTo sb.scalar(true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "pluginOptions" -> {
                        child.children.forEach { option ->
                            if (option.value.startsWith("all-open:annotation=")) {
                                Settings::kotlin {
                                    KotlinSettings::allOpen {
                                        AllOpenSettings::annotations setTo sb.list(
                                            SchemaType.ListType(SchemaType.StringType)
                                        ) {
                                            add(sb.scalar(option.value.substringAfter("all-open:annotation=")))
                                        }
                                    }
                                }
                            }
                            if (option.value.startsWith("all-open:preset=")) {
                                Settings::kotlin {
                                    KotlinSettings::allOpen {
                                        AllOpenSettings::presets setTo sb.list(
                                            SchemaType.ListType(SchemaType.StringType)
                                        ) {
                                            add(sb.scalar(option.value.substringAfter("all-open:preset=")))
                                        }
                                    }
                                }
                            }

                            if (option.value.startsWith("no-arg:annotation=")) {
                                Settings::kotlin {
                                    KotlinSettings::noArg {
                                        NoArgSettings::annotations setTo sb.list(
                                            SchemaType.ListType(SchemaType.StringType)
                                        ) {
                                            add(sb.scalar(option.value.substringAfter("no-arg:annotation=")))
                                        }
                                    }
                                }
                            }

                            if (option.value.startsWith("no-arg:preset=")) {
                                Settings::kotlin {
                                    KotlinSettings::noArg {
                                        NoArgSettings::presets setTo sb.list(
                                            SchemaType.ListType(SchemaType.StringType)
                                        ) {
                                            add(sb.scalar(option.value.substringAfter("no-arg:preset=")))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "jvmTarget" -> {
                        Settings::jvm {
                            JvmSettings::release setTo sb.scalar(child.value)
                        }
                    }
                    "javaParameters" -> {
                        Settings::jvm {
                            JvmSettings::storeParameterNames setTo sb.scalar(child.value.toBoolean())
                        }
                    }
                }
            }
        }
    }
}