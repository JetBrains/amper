/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.intellij

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.lang.LanguageASTFactory
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.ReadActionCache
import com.intellij.util.ProcessingContext
import com.jetbrains.apple.sdk.AppleSdkManagerBase
import com.jetbrains.cidr.xcode.CoreXcodeProjectEnvironment
import com.jetbrains.cidr.xcode.XcodeBase
import com.jetbrains.cidr.xcode.XcodeSettingsBase
import com.jetbrains.cidr.xcode.frameworks.AppleSdkManager
import com.jetbrains.cidr.xcode.model.PBXReferenceBuildSettingProvider
import com.jetbrains.cidr.xcode.xcspec.XcodeExtensionsManager
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

    @TestOnly
    var chosenConfigurator: IntelliJApplicationConfigurator = IntelliJApplicationConfigurator()

    /**
     * Initialized lazily on demand.
     */
    val mockProject: Project by lazy {
        initMockProject(chosenConfigurator)
    }

    private lateinit var ourProject: Project

    private var latestConfigurator: IntelliJApplicationConfigurator? = null

    fun initMockProject(intelliJApplicationConfigurator: IntelliJApplicationConfigurator): Project {
        if (ApplicationManager.getApplication() != null && latestConfigurator == intelliJApplicationConfigurator) {
            // Init application and factory in standalone non-IDE environment only
            return ourProject
        }

        System.setProperty("idea.home.path", "") // TODO: Is it correct?

        // Set up app env.
        val appEnv = CoreApplicationEnvironment(Disposer.newDisposable())

        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            PBXReferenceBuildSettingProvider.EP_NAME,
            PBXReferenceBuildSettingProvider::class.java
        )
        appEnv.application.registerService(XcodeSettingsBase::class.java)
        appEnv.application.registerService(XcodeBase::class.java)
        appEnv.application.registerService(XcodeExtensionsManager::class.java)
        appEnv.application.registerService(AppleSdkManagerBase::class.java, AppleSdkManager::class.java)

        appEnv.registerLangAppServices()
        appEnv.application.registerService(ReadActionCache::class.java, ReadActionCacheImpl())
        XcodeSettingsBase.INSTANCE.setSelectedXcodePath(detectXcodeInstallation())
        intelliJApplicationConfigurator.registerApplicationExtensions(appEnv.application)

        // Set up project env.
        val projectEnv = CoreXcodeProjectEnvironment(appEnv.parentDisposable, appEnv)
        intelliJApplicationConfigurator.registerProjectExtensions(projectEnv.project)

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