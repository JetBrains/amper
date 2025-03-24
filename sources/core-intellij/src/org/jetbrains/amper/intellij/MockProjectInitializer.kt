/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.intellij

import com.intellij.amper.lang.AmperLanguage
import com.intellij.amper.langImpl.AmperFileType
import com.intellij.amper.langImpl.AmperParserDefinition
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreProjectEnvironment
import com.intellij.lang.LanguageASTFactory
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.ReadActionCache
import com.intellij.util.ProcessingContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.useWithoutCoroutines
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.YAMLParserDefinition
import org.toml.lang.TomlLanguage
import org.toml.lang.parse.TomlParserDefinition
import org.toml.lang.psi.TomlFileType
import org.toml.lang.psi.impl.TomlASTFactory

open class IntelliJApplicationConfigurator {
    open fun registerApplicationExtensions(application: MockApplication) {}
    open fun registerProjectExtensions(project: MockProject) {}

    companion object {
        val EMPTY = IntelliJApplicationConfigurator()
    }
}

object MockProjectInitializer {

    private lateinit var ourProject: Project

    private var latestConfigurator: IntelliJApplicationConfigurator? = null

    private var ourDisposable: Disposable? = null

    @Synchronized
    fun initMockProject(intelliJApplicationConfigurator: IntelliJApplicationConfigurator): Project {
        val latest = latestConfigurator
        check(latest == null || latest === intelliJApplicationConfigurator) {
            """
                Only one configurator can be used at a time.
                old: ${latest?.javaClass?.name} $latest
                new: ${intelliJApplicationConfigurator.javaClass.name} $intelliJApplicationConfigurator
            """.trimIndent()
        }

        val previousDisposable = ourDisposable
        if (previousDisposable != null) {
            // If the MockProjectInitializer.initMockProject is called more than once within the current JVM session,
            // we want to fully initialize new instances of the IntelliJ MockApplication and the MockProject.
            // Since the Application is a singleton saved in the ApplicationManager.getApplication() we first need
            // to dispose and nullify the previous one.
            //
            // initMockProject can be called twice at least in the following situations:
            // 1. In Gradle-based Amper there is a Gradle demon that reuses the current JVM (and thus doesn't create
            // a new instance of the MockProjectInitializer singleton) between different Amper commands.
            // 2. Some tests create new instances of the FrontendPathResolver several times,
            // and each FrontendPathResolver calls initMockProject() thus reusing the Applications.

            Disposer.dispose(previousDisposable)
            @Suppress("UnstableApiUsage")
            ApplicationManager.setApplication(null)
        }

        val previousApplication = ApplicationManager.getApplication()
        if (previousApplication != null && !previousApplication.isDisposed) {
            // Init application and factory in standalone non-IDE environment only
            return ourProject
        }

        return spanBuilder("Init mock IntelliJ project").useWithoutCoroutines {
            System.setProperty("idea.home.path", "") // TODO: Is it correct?

            val disposable = Disposer.newDisposable()
            val appEnv = CoreApplicationEnvironment(disposable)
            ourDisposable = disposable
            appEnv.registerLangAppServices()
            @Suppress("UnstableApiUsage")
            appEnv.application.registerService(ProgressManager::class.java, MockProgressManager::class.java)
            appEnv.application.registerService(ReadActionCache::class.java, ReadActionCacheImpl())
            intelliJApplicationConfigurator.registerApplicationExtensions(appEnv.application)

            val projectEnv = CoreProjectEnvironment(appEnv.parentDisposable, appEnv)
            intelliJApplicationConfigurator.registerProjectExtensions(projectEnv.project)

            @Suppress("UnstableApiUsage")
            Registry.markAsLoaded()

            latestConfigurator = intelliJApplicationConfigurator
            projectEnv.project.also { ourProject = it }
        }
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

    private class MockProgressManager : CoreProgressManager() {
        override fun doCheckCanceled() {
            runBlocking {
                currentCoroutineContext().ensureActive()
            }
        }
    }

    // Copy-pasted from IDEA (lack of this service causes PSI Scalar elements textValue calculation failure)
    @Suppress("UnstableApiUsage")
    private class ReadActionCacheImpl : ReadActionCache {
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
            } else try {
                writeActionProcessingContext = ProcessingContext()
                supplier.invoke()
            } finally {
                writeActionProcessingContext = null
            }
        }

        override fun allowInWriteAction(runnable: Runnable) {
            allowInWriteAction(runnable::run)
        }
    }
}
