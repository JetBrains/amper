package org.jetbrains.amper.gradle

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
import java.lang.reflect.Field

private const val DYNAMIC_PLUGINS_CLASSPATH = "dynamicPluginsClasspath"
private const val DYNAMIC_PLUGINS_RESOLVED_CLASSPATH = "dynamicPluginsResolvedClasspath"

/**
 * Try to add plugins dynamically to be accessible within
 * project scripts classpath.
 */
fun Settings.setupDynamicPlugins(
    vararg plugins: String
) {
    val internalGradle = gradle as GradleInternal
    internalGradle.setupDynamicPlugins(this as SettingsInternal, *plugins)
}

fun GradleInternal.setupDynamicPlugins(
    settings: SettingsInternal,
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
    val resolveConfig = configurationContainer.resolvable(DYNAMIC_PLUGINS_RESOLVED_CLASSPATH) {
        it.extendsFrom(classpathConfig)
    }.get()

    // Adjust configurations.
    plugins.forEach { dependencyHandler.add(DYNAMIC_PLUGINS_CLASSPATH, it) }
    dependencyResolution.resolveRepositoryHandler.apply { mavenCentral() }
    classPathResolver.prepareClassPath(classpathConfig, dependencyHandler)

    // Resolve classpath.
    val foundClassPath = classPathResolver.resolveClassPath(resolveConfig)

    // Set classpath for settings ClassLoaderScope.
    // (so that, this classpath will be accessible in project scripts)
    val settingsAdapter = SettingsAdapter(settings)
    settingsAdapter.classLoaderScope = settingsAdapter.classLoaderScope
        .createChild("amper", null)
        .export(foundClassPath)
        .lock()
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