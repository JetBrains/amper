/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.standalone

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreProjectEnvironment
import com.intellij.lang.LanguageASTFactory
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.ReadActionCache
import com.intellij.util.ProcessingContext
import org.jetbrains.amper.frontend.IntelliJApplicationConfigurator
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.YAMLParserDefinition
import org.toml.lang.TomlLanguage
import org.toml.lang.parse.TomlParserDefinition
import org.toml.lang.psi.TomlFileType
import org.toml.lang.psi.impl.TomlASTFactory

private lateinit var ourProject: Project
private var latestConfigurator: IntelliJApplicationConfigurator? = null

fun initMockProject(intelliJApplicationConfigurator: IntelliJApplicationConfigurator): Project {
  if (ApplicationManager.getApplication() != null && latestConfigurator == intelliJApplicationConfigurator) {
    // Init application and factory in standalone non-IDE environment only
    return ourProject
  }

  System.setProperty("idea.home.path", "") // TODO: Is it correct?

  val appEnv = CoreApplicationEnvironment(Disposer.newDisposable())
  val projectEnv = CoreProjectEnvironment(appEnv.parentDisposable, appEnv)

  @Suppress("UnstableApiUsage")
  appEnv.application.registerService(ReadActionCache::class.java, ReadActionCacheImpl())

  // Register YAML support
  LanguageParserDefinitions.INSTANCE.addExplicitExtension(YAMLLanguage.INSTANCE, YAMLParserDefinition())
  LanguageParserDefinitions.INSTANCE.addExplicitExtension(TomlLanguage, TomlParserDefinition())
  appEnv.registerFileType(YAMLFileType.YML, "yaml")

  // Register TOML support
  LanguageASTFactory.INSTANCE.addExplicitExtension(TomlLanguage, TomlASTFactory())
  appEnv.registerFileType(TomlFileType, "toml")

  intelliJApplicationConfigurator.registerApplicationExtensions(appEnv.application)
  intelliJApplicationConfigurator.registerProjectExtensions(projectEnv.project)

//  RegistryKeyBean.addKeysFromPlugins()
  @Suppress("UnstableApiUsage")
  Registry.markAsLoaded()

  latestConfigurator = intelliJApplicationConfigurator
  return projectEnv.project.also { ourProject = it }
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