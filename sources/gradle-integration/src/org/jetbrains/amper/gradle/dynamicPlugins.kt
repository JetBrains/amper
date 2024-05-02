/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext
import org.gradle.api.internal.initialization.ScriptClassPathResolver
import org.gradle.initialization.DefaultSettings
import org.gradle.internal.classpath.ClassPath
import java.lang.reflect.Field

private const val DYNAMIC_PLUGINS_CLASSPATH = "dynamicPluginsClasspath"

/**
 * Try to add plugins dynamically to be accessible within
 * project scripts classpath.
 */
fun Settings.setupDynamicPlugins(
    vararg plugins: String,
    adjustDependencies: RepositoryHandler.() -> Unit,
) {
    val internalGradle = gradle as GradleInternal
    internalGradle.doSetupDynamicPlugins(this as SettingsInternal, adjustDependencies, *plugins)
}

private fun GradleInternal.doSetupDynamicPlugins(
    settings: SettingsInternal,
    adjustDependencies: RepositoryHandler.() -> Unit,
    vararg plugins: String,
) {
    // Prepare services.
    val classPathResolver = services.get(ScriptClassPathResolver::class.java)
    val dependencyResolution = services.get(DependencyManagementServices::class.java)
        .create(
            services.get(FileResolver::class.java),
            services.get(FileCollectionFactory::class.java),
            services.get(DependencyMetaDataProvider::class.java),
            services.get(ProjectFinder::class.java),
            RootScriptDomainObjectContext.INSTANCE,
        )
    val dependencyHandler = dependencyResolution.dependencyHandler

    // Create configurations.
    val configurationContainer = dependencyResolution.configurationContainer as RoleBasedConfigurationContainerInternal
    val classpathConfig = configurationContainer.create(DYNAMIC_PLUGINS_CLASSPATH)

    // Adjust configurations.
    plugins.forEach { dependencyHandler.add(DYNAMIC_PLUGINS_CLASSPATH, it) }
    dependencyResolution.resolveRepositoryHandler.adjustDependencies()

    // TODO Investigate why required attributes are not found for compose.
//    classPathResolver.prepareClassPath(classpathConfig, dependencyHandler)

    // Resolve classpath.
    val foundClassPath = resolveClassPath(classPathResolver, classpathConfig, dependencyHandler, configurationContainer)

    // Set classpath for settings ClassLoaderScope.
    // (so that, this classpath will be accessible in project scripts)
    val settingsAdapter = SettingsAdapter(settings)
    settingsAdapter.classLoaderScope = settingsAdapter.classLoaderScope
        .createChild("amper", null)
        .export(foundClassPath)
        .lock()
}

private fun resolveClassPath(
    classPathResolver: ScriptClassPathResolver,
    classpathConfig: Configuration?,
    dependencyHandler: DependencyHandler?,
    configurationContainer: RoleBasedConfigurationContainerInternal
): ClassPath {
    val resolver = classPathResolver.javaClass.declaredMethods.filter {
        it.name == "resolveClassPath"
    }.single()

    // breaking internal API changes in both Gradle 8.6 and 8.7
    // we want to support all of Gradle < 8.6, Gradle = 8.6 and Gradle 8.7+
    val foundClassPath = when (resolver.parameters.size) {
        1 -> {
            // Gradle < 8.6 - one parameter
            resolver.invoke(classPathResolver, classpathConfig)
        }
        2 -> {
            // Gradle 8.7+ - two parameters
            val prepareHandler = classPathResolver.javaClass.declaredMethods.filter {
                it.name == "prepareDependencyHandler"
            }.single()
            resolver.invoke(classPathResolver, prepareHandler.invoke(
                classPathResolver, dependencyHandler
            ))
        }
        else -> {
            // Gradle 8.6 - three parameters :D
            resolver.invoke(classPathResolver, classpathConfig, dependencyHandler, configurationContainer)
        }
    } as ClassPath
    return foundClassPath
}

/**
 * Settings adapter to change chosen settings class loader scope.
 */
class SettingsAdapter(
    private val delegate: SettingsInternal
) {
    companion object {
        val classLoaderScopeField: Field = DefaultSettings::class.java
            .getDeclaredField("classLoaderScope")
            .apply { isAccessible = true }
    }

    var classLoaderScope: ClassLoaderScope
        get() = classLoaderScopeField.get(delegate) as ClassLoaderScope
        set(value) = run { classLoaderScopeField.set(delegate, value) }

}