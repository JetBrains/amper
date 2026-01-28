/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.types.maven.amperMavenPluginId
import org.jetbrains.amper.maven.publish.createPlexusContainer
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ModuleSequenceCtx
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath

// Set up maven tasks only for JVM modules. 
fun ProjectTasksBuilder.setupMavenCompatibilityTasks() {
    // Skip maven tasks registration if we have no maven plugins specified.
    if (context.projectContext.externalMavenPlugins.isEmpty()) return

    allModules().alsoPlatforms(Platform.JVM).withEach {
        setupUmbrellaMavenTasks()
        setupMavenPluginTasks()
    }
}

context(taskBuilder: ProjectTasksBuilder)
private fun ModuleSequenceCtx.setupUmbrellaMavenTasks() {
    // Convenient helper, since we operate only for the JVM platform and specific module.
    operator fun PlatformTaskType.invoke(isTest: Boolean) = getTaskName(module, Platform.JVM, isTest)

    // Register "before" and "after" tasks for each phase.
    KnownMavenPhase.entries.forEach { phase ->
        // Register before task
        taskBuilder.tasks.registerTask(
            task = phase.createBeforeTask(),
            dependsOn = listOfNotNull(phase.dependsOn?.afterTaskName),
        )

        // Register after task (depends on before task)
        taskBuilder.tasks.registerTask(
            task = AfterMavenPhaseTask(
                taskName = phase.afterTaskName,
                module = module,
                isTest = phase.isTest,
            ),
            dependsOn = listOfNotNull(phase.beforeTaskName, phase.dependsOn?.afterTaskName),
        )
    }

    // Our source generation is aware of external module dependencies, so we
    // shall provide a corresponding task dependency for maven phases as well.
    KnownMavenPhase.`generate-sources`.beforeTaskName dependsOn CommonTaskType.Dependencies(isTest = false)
    KnownMavenPhase.`generate-test-sources`.beforeTaskName dependsOn CommonTaskType.Dependencies(isTest = true)

    // Generated sources/resources procession.
    CommonTaskType.Compile(isTest = false) dependsOn KnownMavenPhase.`process-sources`.afterTaskName
    CommonTaskType.Compile(isTest = false) dependsOn KnownMavenPhase.`process-resources`.afterTaskName

    // Compilation.
    KnownMavenPhase.compile.beforeTaskName dependsOn CommonTaskType.Compile(isTest = false)

    // Before we will return classes or jars, we need to run maven compile phase plugins.
    CommonTaskType.Classes(isTest = false) dependsOn KnownMavenPhase.compile.afterTaskName
    CommonTaskType.Jar(isTest = false) dependsOn KnownMavenPhase.compile.afterTaskName

    // Generated test sources/resources procession.
    CommonTaskType.Compile(isTest = true) dependsOn KnownMavenPhase.`process-test-sources`.afterTaskName
    CommonTaskType.Compile(isTest = true) dependsOn KnownMavenPhase.`process-test-resources`.afterTaskName

    // Test compilation.
    KnownMavenPhase.`test-compile`.beforeTaskName dependsOn CommonTaskType.Compile(isTest = true)

    // Before we will run tests, we need to run maven `test-compile` phase plugins.
    CommonTaskType.Test(isTest = false) dependsOn KnownMavenPhase.`test-compile`.afterTaskName
}

context(taskBuilder: ProjectTasksBuilder)
private fun ModuleSequenceCtx.setupMavenPluginTasks() {
    module.amperMavenPluginsDescriptions.forEach plugin@{ pluginDescription ->
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
            artifactId = pluginDescription.artifactId
            groupId = pluginDescription.groupId
            version = pluginDescription.version
            dependencies = pluginDescription.dependencies.map {
                MavenDependency(
                    groupId = it.groupId,
                    artifactId = it.artifactId,
                    version = it.version,
                    type = "jar",
                    scope = MavenArtifact.SCOPE_RUNTIME,
                )
            }
        }

        // Create mojo execution tasks.
        val mojoTasks = pluginDescription.mojos.mapNotNull { mojo ->
            val mavenCompatPluginId = amperMavenPluginId(pluginDescription, mojo)

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
            val phase = mojoTask.mojo.phase?.let(KnownMavenPhase::get) ?: KnownMavenPhase.verify

            mojoTask.taskName dependsOn phase.beforeTaskName
            phase.afterTaskName dependsOn mojoTask.taskName
        }
    }
}

// Helper functions.
context(taskBuilder: ProjectTasksBuilder, _: ModuleSequenceCtx)
infix fun TaskName.dependsOn(dependsOn: TaskName) =
    taskBuilder.tasks.registerDependency(this, dependsOn)