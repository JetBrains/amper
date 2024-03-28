/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.intellij

import com.intellij.amper.lang.AmperFileType
import com.intellij.amper.lang.AmperLanguage
import com.intellij.amper.lang.AmperParserDefinition
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreProjectEnvironment
import com.intellij.lang.LanguageASTFactory
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ReadActionCache
import com.intellij.util.ProcessingContext
import com.jetbrains.apple.sdk.AppleSdkManagerBase
import com.jetbrains.cidr.xcode.XcodeBase
import com.jetbrains.cidr.xcode.XcodeComponentManager
import com.jetbrains.cidr.xcode.XcodeProjectId
import com.jetbrains.cidr.xcode.XcodeSettingsBase
import com.jetbrains.cidr.xcode.cache.CachedValuesManagerImpl
import com.jetbrains.cidr.xcode.frameworks.AppleFileTypeManager
import com.jetbrains.cidr.xcode.frameworks.AppleSdkManager
import com.jetbrains.cidr.xcode.model.CoreXcodeWorkspace
import com.jetbrains.cidr.xcode.model.XcodeProjectTrackers
import com.jetbrains.cidr.xcode.xcspec.XcodeExtensionsManager
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.annotations.TestOnly
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.YAMLParserDefinition
import org.toml.lang.TomlLanguage
import org.toml.lang.parse.TomlParserDefinition
import org.toml.lang.psi.TomlFileType
import org.toml.lang.psi.impl.TomlASTFactory


@TestOnly
open class IntelliJApplicationConfigurator {
    open fun registerApplicationExtensions(application: MockApplication) {}
    open fun registerProjectExtensions(project: MockProject) {}
}

object MockProjectInitializer {

    private lateinit var ourProject: Project

    private var latestConfigurator: IntelliJApplicationConfigurator? = null

    fun initMockProject(intelliJApplicationConfigurator: IntelliJApplicationConfigurator): Project {
        if (ApplicationManager.getApplication() != null && latestConfigurator == intelliJApplicationConfigurator) {
            // Init application and factory in standalone non-IDE environment only
            return ourProject
        }

        System.setProperty("idea.home.path", "") // TODO: Is it correct?

        val appEnv = CoreApplicationEnvironment(Disposer.newDisposable())
        appEnv.registerLangAppServices()
        appEnv.application.registerService(ReadActionCache::class.java, ReadActionCacheImpl())
        intelliJApplicationConfigurator.registerApplicationExtensions(appEnv.application)

        val projectEnv = CoreProjectEnvironment(appEnv.parentDisposable, appEnv)
        intelliJApplicationConfigurator.registerProjectExtensions(projectEnv.project)

        if (DefaultSystemInfo.detect().family == SystemInfo.OsFamily.MacOs) {
            StandaloneXcodeComponentManager.registerManager(detectXcodeInstallation())
        }

        @Suppress("UnstableApiUsage")
        Registry.markAsLoaded()

        latestConfigurator = intelliJApplicationConfigurator
        return projectEnv.project.also { ourProject = it }
    }

    private fun CoreApplicationEnvironment.registerLangAppServices() {
        // Register YAML support
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(YAMLLanguage.INSTANCE, YAMLParserDefinition())
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(TomlLanguage, TomlParserDefinition())
        registerFileType(YAMLFileType.YML, "yaml")

        // Register TOML support
        LanguageASTFactory.INSTANCE.addExplicitExtension(TomlLanguage, TomlASTFactory())
        registerFileType(TomlFileType, "toml")

        LanguageParserDefinitions.INSTANCE.addExplicitExtension(AmperLanguage.INSTANCE, AmperParserDefinition())
        registerFileType(AmperFileType.INSTANCE, "amper")
    }

    // Copy-pasted from IDEA (lack of this service causes PSI Scalar elements textValue calculation failure)
    @Suppress("UnstableApiUsage")
    private class ReadActionCacheImpl: ReadActionCache {
        private val threadProcessingContext: ThreadLocal<ProcessingContext> = ThreadLocal()

        override val processingContext: ProcessingContext?
            get() {
                threadProcessingContext.get()?.let { return it }
                if (ApplicationManager.getApplication().isWriteIntentLockAcquired) return writeActionProcessingContext
                if (!ApplicationManager.getApplication().isReadAccessAllowed) return null
                threadProcessingContext.set(ProcessingContext())
                return threadProcessingContext.get()
            }

        private var writeActionProcessingContext: ProcessingContext? = null

        override fun <T> allowInWriteAction(supplier: () -> T): T {
            return if (!ApplicationManager.getApplication().isWriteIntentLockAcquired || writeActionProcessingContext != null) {
                supplier.invoke()
            }
            else try {
                writeActionProcessingContext = ProcessingContext()
                supplier.invoke()
            }
            finally {
                writeActionProcessingContext = null
            }
        }

        override fun allowInWriteAction(runnable: Runnable) {
            allowInWriteAction(runnable::run)
        }
    }
}

class StandaloneXcodeComponentManager(private val xcodePath: String, private val unitTestMode: Boolean) :
    XcodeComponentManager {
    override fun isUnitTestMode(): Boolean = unitTestMode

    private val services = mutableMapOf<Class<*>, Any>()

    override fun <T : Any> getService(clazz: Class<T>): T {
        return services.getOrPut(clazz) {
            when {
                XcodeExtensionsManager::class.java.isAssignableFrom(clazz) -> XcodeExtensionsManager()
                XcodeProjectTrackers::class.java.isAssignableFrom(clazz) -> XcodeProjectTrackers()
                CoreXcodeWorkspace::class.java.isAssignableFrom(clazz) -> CoreXcodeWorkspace.EMPTY()
                AppleFileTypeManager::class.java.isAssignableFrom(clazz) -> AppleFileTypeManager()
                AppleSdkManagerBase::class.java.isAssignableFrom(clazz) -> AppleSdkManager()
                CachedValuesManager::class.java.isAssignableFrom(clazz) -> CachedValuesManagerImpl()
                XcodeSettingsBase::class.java.isAssignableFrom(clazz) -> XcodeSettingsBase().also { settings ->
                    settings.setSelectedXcodeBasePath(xcodePath)
                }

                XcodeBase::class.java.isAssignableFrom(clazz) -> XcodeBase()
                else -> throw RuntimeException(":(")
            }
        } as T
    }

    override fun <T : Any> getExtensions(ep: XcodeComponentManager.EP<T>): List<T> {
        return emptyList()
    }

    companion object {
        fun registerManager(xcodePath: String, unitTestMode: Boolean = false) {
            XcodeComponentManager.registerImpl(object : XcodeComponentManager.Initializer {
                private val appMan = StandaloneXcodeComponentManager(xcodePath, unitTestMode)
                private val proMan = StandaloneXcodeComponentManager(xcodePath, unitTestMode)

                override val applicationManager: XcodeComponentManager
                    get() = appMan

                override fun getProjectManager(projectId: XcodeProjectId): XcodeComponentManager {
                    return proMan
                }
            })
        }
    }
}
