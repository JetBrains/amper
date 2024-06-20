/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android.gradle/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.LibraryVariant
import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.Property
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.amper.android.AndroidBuildRequest
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleDependency
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.android.gradle.tooling.ApkPathToolingModelBuilder
import org.jetbrains.amper.android.gradle.tooling.RClassToolingModelBuilder
import java.io.File
import java.nio.file.Path
import javax.inject.Inject
import javax.xml.stream.XMLEventFactory
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory
import kotlin.io.path.div
import kotlin.io.path.isSameFileAs
import kotlin.io.path.relativeTo


interface AmperAndroidIntegrationExtension {
    val jsonData: Property<String>
}

private const val PROJECT_TO_MODULE_EXT = "org.jetbrains.amper.gradle.android.ext.projectToModule"
private const val MODULE_TO_PROJECT_EXT = "org.jetbrains.amper.gradle.android.ext.moduleToProject"
private const val ANDROID_REQUEST = "org.jetbrains.amper.gradle.android.ext.androidRequest"
private const val KNOWN_MODEL_EXT = "org.jetbrains.amper.gradle.android.ext.model"

fun <K, V> ExtraPropertiesExtension.getBindingMap(name: String) = try {
    this[name] as MutableMap<K, V>
} catch (cause: ExtraPropertiesExtension.UnknownPropertyException) {
    val bindingMap = mutableMapOf<K, V>()
    this[name] = bindingMap
    bindingMap
}

val Gradle.projectPathToModule: MutableMap<String, PotatoModule>
    get() = (this as ExtensionAware).extensions.extraProperties.getBindingMap(PROJECT_TO_MODULE_EXT)

val Gradle.moduleFilePathToProject: MutableMap<Path, String>
    get() = (this as ExtensionAware).extensions.extraProperties.getBindingMap(MODULE_TO_PROJECT_EXT)

var Gradle.request: AndroidBuildRequest?
    get() = (this as ExtensionAware).extensions.extraProperties.get(ANDROID_REQUEST) as? AndroidBuildRequest
    set(value) = (this as ExtensionAware).extensions.extraProperties.set(ANDROID_REQUEST, value)

var Gradle.knownModel: Model?
    get() = (this as ExtensionAware).extensions.extraProperties[KNOWN_MODEL_EXT] as? Model
    set(value) {
        (this as ExtensionAware).extensions.extraProperties[KNOWN_MODEL_EXT] = value
    }


val PotatoModule.buildFile get() = (source as PotatoModuleFileSource).buildFile

val PotatoModule.buildDir get() = buildFile.parent

class AmperAndroidIntegrationProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(SLF4JProblemReporterContext()) {
        val rootProjectBuildDir = project.rootProject.layout.buildDirectory.asFile.get().toPath()
        val buildDir = rootProjectBuildDir / project.path.replace(":", "_")
        project.layout.buildDirectory.set(buildDir.toFile())
        project.repositories.google()
        project.repositories.mavenCentral()
        project.gradle.projectPathToModule[project.path]?.let { module ->
            when (module.type) {
                ProductType.ANDROID_APP -> project.plugins.apply("com.android.application")
                else -> project.plugins.apply("com.android.library")
            }
            project.extensions.findByType(BaseExtension::class.java)?.let { androidExtension ->
                module
                    .fragments
                    .filterIsInstance<LeafFragment>()
                    .firstOrNull { it.platforms.contains(Platform.ANDROID) }?.let { androidFragment ->

                        val androidSettings = androidFragment.settings.android
                        androidExtension.compileSdkVersion(androidSettings.compileSdk.versionNumber)
                        androidExtension.defaultConfig {
                            it.maxSdk = androidSettings.maxSdk.versionNumber
                            it.targetSdk = androidSettings.targetSdk.versionNumber
                            it.minSdk = androidSettings.minSdk.versionNumber
                            it.versionCode = 1
                            if (module.type == ProductType.ANDROID_APP) {
                                it.applicationId =  androidSettings.applicationId
                            }
                        }
                        androidExtension.namespace = androidSettings.namespace

                        val requestedModules = project
                            .gradle
                            .request
                            ?.modules
                            ?.associate { it.modulePath to it } ?: mapOf()

                        androidExtension.sourceSets.matching { it.name == "main" }.all {
                            it.manifest.srcFile(androidFragment.src.resolve("AndroidManifest.xml"))
                            it.res.setSrcDirs(setOf(module.buildDir.resolve("res")))
                        }

                        project.afterEvaluate {
                            // get variants
                            val variants = when (module.type) {
                                ProductType.ANDROID_APP -> (androidExtension as AppExtension).applicationVariants
//                                ProductType.ANDROID_INSTRUMENTATION_TESTS -> (androidExtension as AppExtension).testVariants
                                else -> (androidExtension as LibraryExtension).libraryVariants
                            }
                            // choose variant

                            val buildTypes = (project.gradle.request?.buildTypes ?: emptySet()).map { it.value }

                            val chosenVariants =  variants
                                .mapNotNull {
                                    variant -> buildTypes
                                        .firstOrNull { variant.name.startsWith(it) }
                                        ?.let { it to variant }
                                }
                                .toMap()

                            for((buildType, variant) in chosenVariants) {

                                project.tasks.create("prepare$buildType") {
                                    for (output in variant.outputs) {
                                        it.dependsOn(output.processResourcesProvider)
                                    }
                                }

                                project.tasks.create("build$buildType") {
                                    when (variant) {
                                        is ApkVariant -> it.dependsOn(variant.packageApplicationProvider)
                                        is LibraryVariant -> it.dependsOn(variant.packageLibraryProvider)
                                    }
                                }

                                requestedModules[project.path]?.let { requestedModule ->
                                    // set dependencies
                                    for (dependency in requestedModule.resolvedAndroidRuntimeDependencies) {
                                        variant.runtimeConfiguration.dependencies.add(
                                            ResolvedAmperDependency(
                                                project,
                                                dependency
                                            )
                                        )
                                    }

                                    // set inter-module dependencies between android modules
                                    val androidDependencyPaths = project.gradle.knownModel?.let { model ->
                                        androidFragment
                                            .externalDependencies
                                            .asSequence()
                                            .filterIsInstance<PotatoModuleDependency>()
                                            .map { it.module }
                                            .filter { it.artifacts.any { Platform.ANDROID in it.platforms } }
                                            .mapNotNull { project.gradle.moduleFilePathToProject[it.buildDir] }
                                            .filter { it in requestedModules }
                                            .toList()
                                    } ?: listOf()

                                    for (path in androidDependencyPaths) {
                                        variant.runtimeConfiguration.dependencies.add(project.dependencies.project(mapOf("path" to path)))
                                    }

                                    // set classes
                                    requestedModule.moduleClasses.forEach {
                                        variant.registerPostJavacGeneratedBytecode(project.files(it))
                                    }
                                }
                            }
                        }
                    }
            }
        } ?: Unit
    }
}

class AmperAndroidIntegrationSettingsPlugin @Inject constructor(private val toolingModelBuilderRegistry: ToolingModelBuilderRegistry) :
    Plugin<Settings> {
    override fun apply(settings: Settings) = with(SLF4JProblemReporterContext()) {
        registerToolingModelBuilders()

        val extension = settings.extensions.create("androidData", AmperAndroidIntegrationExtension::class.java)

        val loader = Thread.currentThread().contextClassLoader
        settings.gradle.settingsEvaluated {
            val request = Json.decodeFromString<AndroidBuildRequest>(extension.jsonData.get())
            settings.gradle.request = request
            initProjects(request.root, settings, loader)
        }

        settings.gradle.beforeProject { project ->
            adjustXmlFactories()
            settings.gradle.projectPathToModule[project.path]?.let { module ->
                val productTypeIsAndroidApp = module.type == ProductType.ANDROID_APP
                val productTypeIsLib = module.type == ProductType.LIB
                val platformsContainAndroid = module.artifacts.any { it.platforms.contains(Platform.ANDROID) }
                if (productTypeIsAndroidApp || productTypeIsLib && platformsContainAndroid) {
                    project.plugins.apply(AmperAndroidIntegrationProjectPlugin::class.java)
                }
            }
        }
    }

    private fun registerToolingModelBuilders() {
        toolingModelBuilderRegistry.register(ApkPathToolingModelBuilder())
        toolingModelBuilderRegistry.register(RClassToolingModelBuilder())
    }

    context(SLF4JProblemReporterContext)
    private fun initProjects(projectRoot: Path, settings: Settings, loader: ClassLoader) {
        val model = when (val result = ModelInit.getModel(projectRoot, loader = loader)) {
            is Result.Failure -> throw result.exception
            is Result.Success -> result.value
        }

        settings.gradle.knownModel = model

        val rootPath = projectRoot.normalize().toAbsolutePath()
        val androidModules = model
            .modules
            .filter {
                val productTypeIsAndroidApp = it.type == ProductType.ANDROID_APP
                val productTypeIsLib = it.type == ProductType.LIB
                val platformsContainAndroid = it.artifacts.any { it.platforms.contains(Platform.ANDROID) }
                productTypeIsAndroidApp || productTypeIsLib && platformsContainAndroid
            }
            .sortedBy { it.buildFile }

        fun Path.toGradlePath() = ":" + relativeTo(rootPath).toString().replace(File.separator, ":")

        androidModules.forEach {
            val currentPath = it.buildDir.normalize().toAbsolutePath()
            val projectPath = if (currentPath.isSameFileAs(rootPath)) {
                ":"
            } else {
                currentPath.toGradlePath()
            }
            if (projectPath != ":") {
                settings.include(projectPath)
                val project = settings.project(projectPath)
                project.projectDir = it.buildDir.toFile()
            }

            settings.gradle.projectPathToModule[projectPath] = it
            settings.gradle.moduleFilePathToProject[it.buildDir] = projectPath
        }
    }
}

fun trySetSystemProperty(key: String, value: String) {
    if (System.getProperty(key) == null)
        System.setProperty(key, value)
}

fun adjustXmlFactories() {
    trySetSystemProperty(
        XMLInputFactory::class.qualifiedName!!,
        "com.sun.xml.internal.stream.XMLInputFactoryImpl"
    )
    trySetSystemProperty(
        XMLOutputFactory::class.qualifiedName!!,
        "com.sun.xml.internal.stream.XMLOutputFactoryImpl"
    )
    trySetSystemProperty(
        XMLEventFactory::class.qualifiedName!!,
        "com.sun.xml.internal.stream.events.XMLEventFactoryImpl"
    )
}