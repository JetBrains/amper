/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.android.gradle

import com.android.build.gradle.AppExtension
import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.problems.Problems
import org.gradle.api.problems.Severity
import org.gradle.api.provider.Property
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.amper.android.AndroidBuildRequest
import org.jetbrains.amper.android.gradle.tooling.MockableJarModelBuilder
import org.jetbrains.amper.android.gradle.tooling.ProcessResourcesProviderTaskNameToolingModelBuilder
import org.jetbrains.amper.core.get
import org.jetbrains.amper.core.properties.readProperties
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.android.findAndroidManifestFragment
import org.jetbrains.amper.frontend.aomBuilder.SchemaBasedModelImport
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.keyAlias
import org.jetbrains.amper.frontend.schema.keyPassword
import org.jetbrains.amper.frontend.schema.storeFile
import org.jetbrains.amper.frontend.schema.storePassword
import java.io.File
import java.nio.file.Path
import javax.inject.Inject
import javax.xml.stream.XMLEventFactory
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isSameFileAs
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo


interface AmperAndroidIntegrationExtension {
    val jsonData: Property<String>
}

private const val PROJECT_TO_MODULE_EXT = "org.jetbrains.amper.gradle.android.ext.projectToModule"
private const val MODULE_TO_PROJECT_EXT = "org.jetbrains.amper.gradle.android.ext.moduleToProject"
private const val ANDROID_REQUEST = "org.jetbrains.amper.gradle.android.ext.androidRequest"
private const val KNOWN_MODEL_EXT = "org.jetbrains.amper.gradle.android.ext.model"

fun <K, V> ExtraPropertiesExtension.getBindingMap(name: String): MutableMap<K, V> = try {
    @Suppress("UNCHECKED_CAST")
    this[name] as MutableMap<K, V>
} catch (_: ExtraPropertiesExtension.UnknownPropertyException) {
    val bindingMap = mutableMapOf<K, V>()
    this[name] = bindingMap
    bindingMap
}

val Gradle.projectPathToModule: MutableMap<String, AmperModule>
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


val AmperModule.buildFile get() = (source as AmperModuleFileSource).buildFile

val AmperModule.buildDir: Path get() = buildFile.parent

private const val SIGNING_CONFIG_NAME = "sign"

@Suppress("UnstableApiUsage")
class AmperAndroidIntegrationProjectPlugin @Inject constructor(private val problems: Problems) : Plugin<Project> {
    override fun apply(project: Project) {
        val log = project.logger
        val rootProjectBuildDir = project.rootProject.layout.buildDirectory.asFile.get().toPath()
        val buildDir = rootProjectBuildDir / project.path.replace(":", "_")
        project.layout.buildDirectory.set(buildDir.toFile())
        project.repositories.google()
        project.repositories.mavenCentral()
        val module = project.gradle.projectPathToModule[project.path] ?: return

        project.plugins.apply("com.android.application")

        if ((module.buildDir / "google-services.json").exists()) {
            project.plugins.apply("com.google.gms.google-services")
        }

        val androidExtension = project.extensions.findByType(AppExtension::class.java) ?: return
        project.setArtifactBaseName()

        val androidFragment = module
            .fragments
            .filterIsInstance<LeafFragment>()
            .firstOrNull { it.platforms.contains(Platform.ANDROID) } ?: return

        val manifestFragment = androidFragment.findAndroidManifestFragment()

        val androidSettings = androidFragment.settings.android
        androidExtension.compileSdkVersion(androidSettings.compileSdk.versionNumber)

        val signing = androidSettings.signing

        if (signing.enabled) {
            val path = (module.buildDir / signing.propertiesFile.pathString).normalize().absolute()
            if (path.exists()) {
                val keystoreProperties = path.readProperties()
                androidExtension.signingConfigs { configs ->
                    configs.create(SIGNING_CONFIG_NAME) { config ->
                        keystoreProperties.storeFile?.let { storeFile ->
                            config.storeFile = (module.buildDir / Path(storeFile)).toFile()
                        }
                        keystoreProperties.storePassword?.let { storePassword ->
                            config.storePassword = storePassword
                        }
                        keystoreProperties.keyAlias?.let { keyAlias ->
                            config.keyAlias = keyAlias
                        }
                        keystoreProperties.keyPassword?.let { keyPassword ->
                            config.keyPassword = keyPassword
                        }
                    }
                }
            } else {
                problems.reporter.reporting { problem ->
                    problem
                        .id("signing-properties-file-not-found", "Signing properties file not found")
                        .contextualLabel("Signing properties file not found")
                        .details("Signing properties file $path not found. Signing will not be configured")
                        .severity(Severity.WARNING)
                        .solution("Put signing properties file to $path")
                }
                log.warn("Properties file $path not found. Signing will not be configured")
            }
        }

        androidExtension.defaultConfig {
            it.maxSdk = androidSettings.maxSdk?.versionNumber
            it.targetSdk = androidSettings.targetSdk.versionNumber
            it.minSdk = androidSettings.minSdk.versionNumber
            it.versionCode = androidSettings.versionCode
            it.versionName = androidSettings.versionName
            if (module.type == ProductType.ANDROID_APP) {
                it.applicationId = androidSettings.applicationId
            }
        }
        androidExtension.namespace = androidSettings.namespace

        androidExtension.packagingOptions.resources {
            val resourcePackaging = androidSettings.resourcePackaging
            excludes.addAll(resourcePackaging.excludes.map { it.value })
            merges.addAll(resourcePackaging.merges.map { it.value })
            pickFirsts.addAll(resourcePackaging.pickFirsts.map { it.value })
        }

        androidExtension.buildTypes { types ->
            types.getByName("release") { release ->
                release.proguardFiles(
                    androidExtension.getDefaultProguardFile("proguard-android-optimize.txt"),
                    (module.buildDir / "proguard-rules.pro").toFile()
                )
                release.isDebuggable = false
                release.isMinifyEnabled = true
                release.isShrinkResources = true
                androidExtension.signingConfigs.findByName(SIGNING_CONFIG_NAME)?.let { signing ->
                    release.signingConfig = signing
                }
            }
        }

        val requestedModules = project
            .gradle
            .request
            ?.modules
            ?.associate { it.modulePath to it } ?: mapOf()

        androidExtension.sourceSets.matching { it.name == "main" }.all {
            it.manifest.srcFile(manifestFragment.src.resolve("AndroidManifest.xml"))
            it.res.setSrcDirs(setOf(module.buildDir.resolve("res")))
        }

        project.afterEvaluate {

            // get variants
            val variants = androidExtension.applicationVariants
            // choose variant
            val buildTypes = (project.gradle.request?.buildTypes ?: emptySet()).map { it.value }.toSet()
            val chosenVariants = variants.filter { it.name in buildTypes }

            for (variant in chosenVariants) {
                val requestedModule = requestedModules[project.path] ?: return@afterEvaluate

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
                val androidDependencyPaths = project.gradle.knownModel?.let { _ ->
                    androidFragment
                        .externalDependencies
                        .asSequence()
                        .filterIsInstance<LocalModuleDependency>()
                        .map { it.module }
                        .filter { it.artifacts.any { artifact -> Platform.ANDROID in artifact.platforms } }
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

    private fun Project.setArtifactBaseName() {
        val baseExtension = extensions.findByType(BasePluginExtension::class.java) ?: return
        @Suppress("DEPRECATION") // The AGP still uses that
        baseExtension.archivesBaseName = "gradle-project" // The IDE now relies on this name
    }
}

class AmperAndroidIntegrationSettingsPlugin @Inject constructor(private val toolingModelBuilderRegistry: ToolingModelBuilderRegistry) :
    Plugin<Settings> {
    override fun apply(settings: Settings) = with(SLF4JProblemReporterContext()) {
        toolingModelBuilderRegistry.register(ProcessResourcesProviderTaskNameToolingModelBuilder())
        toolingModelBuilderRegistry.register(MockableJarModelBuilder())
        val extension = settings.extensions.create("androidData", AmperAndroidIntegrationExtension::class.java)

        settings.gradle.settingsEvaluated {
            val request = Json.decodeFromString<AndroidBuildRequest>(extension.jsonData.get())
            settings.gradle.request = request
            initProjects(request.root, settings)
        }

        settings.gradle.beforeProject { project ->
            adjustXmlFactories()
            settings.gradle.projectPathToModule[project.path]?.let {
                project.plugins.apply(AmperAndroidIntegrationProjectPlugin::class.java)
            }
        }
    }

    context(SLF4JProblemReporterContext)
    private fun initProjects(projectRoot: Path, settings: Settings) {
        // TODO Instead of importing the Amper model, we could pass the information we need from the Amper CLI.
        //   The interface between the Amper CLI and the Gradle delegate project would be more clearly defined,
        //   and we could use just the relevant subset of the data.
        //   Some pieces of data might even have already been resolved/changed in the Amper CLI, such as dependencies.
        //   and in that case we wouldn't want Gradle to re-read the Amper model files and get it wrong.
        //   Also, it would avoid parsing all modules files in the entire project for each delegated Gradle build.
        val projectContext = StandaloneAmperProjectContext.create(projectRoot, buildDir = null, project = null)
            ?: error("Invalid project root passed to the delegated Android Gradle build: $projectRoot")
        val model = SchemaBasedModelImport.getModel(projectContext).get()

        settings.gradle.knownModel = model

        val rootPath = projectRoot.normalize().toAbsolutePath()
        val androidModules = model
            .modules
            .filter {
                val productTypeIsAndroidApp = it.type == ProductType.ANDROID_APP
                val productTypeIsLib = it.type == ProductType.LIB
                val platformsContainAndroid = it.artifacts.any { artifact -> artifact.platforms.contains(Platform.ANDROID) }
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

                /*
                * AndroidLint in AGP 8.6.X+ now checks if build files exist. For each Amper submodule needed to build
                * an app, we explicitly set the build file (using an internal API) in a synthetic Gradle project within
                * the build folder.
                */
                (project as DefaultProjectDescriptor).buildFileName = currentPath
                    .relativize(settings.rootDir.toPath() / "build.gradle.kts")
                    .toString()
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
