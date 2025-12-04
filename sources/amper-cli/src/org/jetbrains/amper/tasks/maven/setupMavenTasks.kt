/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.types.maven.amperMavenPluginId
import org.jetbrains.amper.maven.publish.createPlexusContainer
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ModuleSequenceCtx
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath

// Set up maven tasks only for JVM modules. 
fun ProjectTasksBuilder.setupMavenCompatibilityTasks() {
    // Skip maven tasks registration if we have no maven plugins specified.
    if (context.projectContext.externalMavenPluginDependencies.isEmpty()) return

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
            dependsOn = listOfNotNull(phase.dependsOn?.taskName),
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
    module.amperMavenPluginsDescriptions.forEach plugin@{ pluginXml ->
        // TODO What actual classloader to place here? Do we even need maven mojos to be aware of
        //  amper classes? Should classes be shared between different maven mojos/plugins?
        //  Even instances of plexus beans that are discovered on the classpath?
        val container = createPlexusContainer(KnownMavenPhase::class.java.classLoader)
        
        // Adjust the Maven API class loader (that is used as a "parent" for plugin classloaders) 
        // so that classes that are already on the Amper classpath won't be loaded twice for 
        // plugins that depend on these classes.
        container.classRealmManager.mavenApiRealm.apply {
            importFrom(container.containerRealm, "org.apache.maven.doxia")
            importFrom(container.containerRealm, "org.apache.maven.reporting")
            importFrom(container.containerRealm, "org.apache.velocity")
        }

        val moduleMavenProject = MockedMavenProject()

        val mavenPlugin = MavenPlugin().apply {
            artifactId = pluginXml.artifactId
            groupId = pluginXml.groupId
            version = pluginXml.version
        }

        // Create mojo execution tasks.
        val mojoTasks = pluginXml.mojos.mapNotNull { mojo ->
            val mavenCompatPluginId = amperMavenPluginId(pluginXml, mojo)

            // There must be a node, at least to read "enabled" property.
            val correspondingNode = module.pluginSettings.valueHolders[mavenCompatPluginId]?.value
                ?: return@mapNotNull null
            if (correspondingNode !is SchemaNode) return@mapNotNull null

            val isEnabled = correspondingNode.valueHolders["enabled"]?.value as? Boolean ?: false
            if (!isEnabled) return@mapNotNull null

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
            taskBuilder.tasks.registerTask(mojoTask)
            // Set the verify phase as default, so it will set up basic knowledge about the project for maven.
            val phase = KnownMavenPhase.entries.singleOrNull { it.name == mojoTask.mojo.phase } ?: KnownMavenPhase.verify
            taskBuilder.tasks.registerDependency(phase.taskName, mojoTask.taskName)
            phase.dependsOn?.let { taskBuilder.tasks.registerDependency(mojoTask.taskName, it.taskName) }
        }
    }
}
