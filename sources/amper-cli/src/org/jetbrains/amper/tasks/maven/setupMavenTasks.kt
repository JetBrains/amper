/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.eclipse.aether.impl.ArtifactResolver
import org.eclipse.aether.impl.DependencyCollector
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.asMapLikeAndGet
import org.jetbrains.amper.frontend.types.maven.mavenCompatPluginId
import org.jetbrains.amper.maven.publish.createPlexusContainer
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ModuleSequenceCtx
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath

// Set up maven tasks only for JVM modules. 
fun ProjectTasksBuilder.setupMavenCompatibilityTasks() {
    // Skip maven tasks registration if we have no maven plugins specified.
    if (context.projectContext.externalMavenPluginDependencies.isNullOrEmpty()) return

    allModules().alsoPlatforms(Platform.JVM).withEach {
        setupUmbrellaMavenTasks()
        setupMavenPluginTasks()
    }
}

context(taskBuilder: ProjectTasksBuilder)
private fun ModuleSequenceCtx.setupUmbrellaMavenTasks() {
    // Helper functions.
    fun TaskName.dependsOn(vararg dependsOn: KnownMavenPhase) =
        dependsOn.forEach { taskBuilder.tasks.registerDependency(this, it.taskName) }

    fun TaskName.dependencyOf(vararg dependsOn: KnownMavenPhase) =
        dependsOn.forEach { taskBuilder.tasks.registerDependency(it.taskName, this) }

    // Register an umbrella task for each phase.
    KnownMavenPhase.entries.forEach { phase ->
        taskBuilder.tasks.registerTask(
            task = phase.createTask(),
            dependsOn = phase.dependsOn.map { it.taskName },
        )
    }
    
    // Our source generation is aware of external module dependencies, so we
    // shall provide a corresponding task dependency for maven phases as well.
    CommonTaskType.Dependencies.getTaskName(module, Platform.JVM, isTest = false)
        .dependencyOf(KnownMavenPhase.`generate-sources`)
    CommonTaskType.Dependencies.getTaskName(module, Platform.JVM, isTest = true)
        .dependencyOf(KnownMavenPhase.`generate-test-sources`)

    // Generated sources/resources procession.
    CommonTaskType.Compile.getTaskName(module, Platform.JVM, isTest = false)
        .dependsOn(KnownMavenPhase.`process-sources`, KnownMavenPhase.`process-resources`)

    // Compilation.
    CommonTaskType.Compile.getTaskName(module, Platform.JVM, isTest = false)
        .dependencyOf(KnownMavenPhase.compile)

    // Before we will return classes or jars, we need to run maven compile phase plugins.
    CommonTaskType.Classes.getTaskName(module, Platform.JVM, isTest = false)
        .dependsOn(KnownMavenPhase.compile)
    CommonTaskType.Jar.getTaskName(module, Platform.JVM, isTest = false)
        .dependsOn(KnownMavenPhase.compile)

    // Generated test sources/resources procession.
    CommonTaskType.Compile.getTaskName(module, Platform.JVM, isTest = true)
        .dependsOn(KnownMavenPhase.`process-test-sources`, KnownMavenPhase.`process-test-resources`)

    CommonTaskType.Compile.getTaskName(module, Platform.JVM, isTest = true)
        .dependencyOf(KnownMavenPhase.`test-compile`)

    // Before we will run tests, we need to run maven test compile phase plugins.
    CommonTaskType.Test.getTaskName(module, Platform.JVM)
        .dependsOn(KnownMavenPhase.`test-compile`)
}

context(taskBuilder: ProjectTasksBuilder)
private fun ModuleSequenceCtx.setupMavenPluginTasks() {
    val drBridge = AmperMavenDRBridge(MavenResolver(taskBuilder.context.userCacheRoot))

    module.mavenPluginXmls.forEach plugin@{ pluginXml ->
        val container = createPlexusContainer()

        container.addDefaultComponent<DependencyCollector>(drBridge)
        container.addDefaultComponent<ArtifactResolver>(drBridge)

        val moduleMavenProject = MockedMavenProject()

        // TODO Reporting.
        val jvmFragmentSettings = module.leafFragments
            .singleOrNull { it.platform == Platform.JVM && !it.isTest }
            ?.settings ?: return@plugin

        val mavenPlugin = MavenPlugin().apply {
            artifactId = pluginXml.artifactId
            groupId = pluginXml.groupId
            version = pluginXml.version
        }

        // Create mojo execution tasks.
        val mojoTasks = pluginXml.mojos.mapNotNull { mojo ->
            val mavenCompatPluginId = mavenCompatPluginId(pluginXml, mojo)

            val correspondingNode =
                jvmFragmentSettings.valueHolders[mavenCompatPluginId]?.value ?: return@mapNotNull null
            if (correspondingNode !is SchemaNode) return@mapNotNull null

            // TODO Handle enabled property more delicately, since it can be defined both in Amper
            //  and in the plugin configuration.
            val dumpedProperties = correspondingNode.mavenXmlDump(module.source.moduleDir) { key, _ ->
                key != "enabled"
            }.prependIndent("  ")
            
            val configString = "<properties>\n$dumpedProperties\n</properties>"

            val taskName = TaskName.moduleTask(module, mavenCompatPluginId)
            ExecuteMavenMojoTask(
                taskName = taskName,
                module = module,
                buildOutputRoot = taskBuilder.context.getTaskOutputPath(taskName),
                terminal = taskBuilder.context.terminal,
                plexus = container,
                mavenPlugin = mavenPlugin,
                mojo = mojo,
                mavenProject = moduleMavenProject,
                configString = configString,
            )
        }

        // Register mojo execution tasks and link them to the phases.
        mojoTasks.forEach { mojoTask ->
            // Skip mojos without known phases for now.
            val phase = KnownMavenPhase.entries.singleOrNull { it.name == mojoTask.mojo.phase } ?: return@forEach
            taskBuilder.tasks.registerTask(mojoTask)
            taskBuilder.tasks.registerDependency(phase.taskName, mojoTask.taskName)
            phase.dependsOn.forEach { taskBuilder.tasks.registerDependency(mojoTask.taskName, it.taskName) }
        }
    }
}

operator fun TreeValue<Refined>?.get(property: String) = this?.asMapLikeAndGet(property)