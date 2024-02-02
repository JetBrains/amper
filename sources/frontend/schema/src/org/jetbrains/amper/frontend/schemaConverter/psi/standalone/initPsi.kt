/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.standalone

import com.intellij.lang.LanguageASTFactory
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.impl.PsiBuilderFactoryImpl
import com.intellij.mock.MockApplication
import com.intellij.mock.MockFileDocumentManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.ReadActionCache
import com.intellij.util.ProcessingContext
import com.intellij.util.messages.MessageBus
import com.intellij.util.pico.DefaultPicoContainer
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.YAMLParserDefinition
import org.toml.lang.TomlLanguage
import org.toml.lang.parse.TomlParserDefinition
import org.toml.lang.psi.TomlFileType
import org.toml.lang.psi.impl.TomlASTFactory

fun initPsiFileFactory(rootDisposable: Disposable, project: Project) {
  if (ApplicationManager.getApplication() != null) {
    // Init application and factory in standalone non-IDE environment only
    return
  }

  val application = initApplication(rootDisposable)
  val appContainer = application.picoContainer
  appContainer.registerComponentInstance(MessageBus::class.java, application.messageBus)
  application.registerApplicationService(FileDocumentManager::class.java,
//    MockFileDocumentManagerImpl(null) {
//      DocumentImpl(it, SystemInfo.isWindows, false)
//    },
    MockFileDocumentManagerImpl(null, ::DocumentImpl),
    project
  )
  application.registerApplicationService(PsiBuilderFactory::class.java, PsiBuilderFactoryImpl(), project)
  application.registerApplicationService(ProgressManager::class.java, CoreProgressManager(), project)

  LanguageParserDefinitions.INSTANCE.addExplicitExtension(YAMLLanguage.INSTANCE, YAMLParserDefinition())
  LanguageParserDefinitions.INSTANCE.addExplicitExtension(TomlLanguage, TomlParserDefinition())
  LanguageASTFactory.INSTANCE.addExplicitExtension(TomlLanguage, TomlASTFactory())

  FileTypeRegistry.setInstanceSupplier {
    object: FileTypeRegistry() {
      override fun isFileIgnored(p0: VirtualFile): Boolean {
        TODO("Not yet implemented")
      }

      override fun getRegisteredFileTypes(): Array<FileType> {
        return arrayOf(YAMLFileType.YML, TomlFileType)
      }

      override fun getFileTypeByFile(p0: VirtualFile): FileType {
        if (StringUtil.equalsIgnoreCase(p0.extension, "toml")) return TomlFileType
        return YAMLFileType.YML
      }

      override fun getFileTypeByFileName(p0: String): FileType {
        TODO("Not yet implemented")
      }

      override fun getFileTypeByExtension(p0: String): FileType {
        TODO("Not yet implemented")
      }

      override fun findFileTypeByName(p0: String): FileType {
        TODO("Not yet implemented")
      }
    }
  }
}

private fun <T:Any> MockApplication.registerApplicationService(aClass: Class<T>, `object`: T, project: Project) {
  this.registerService(aClass, `object`)
  Disposer.register(
    project
  ) { this.getPicoContainer().unregisterComponent(aClass.name) }
}

fun <T:Any> registerComponentInstance(container: DefaultPicoContainer, key: Class<T>?, implementation: T): T? {
  val old = container.getComponentInstance(key!!)
  container.unregisterComponent(key)
  container.registerComponentInstance(key, implementation)
  return old as T?
}

fun initApplication(rootDisposable: Disposable): MockApplication {
  val instance = MockApplication(rootDisposable)
  instance.registerService(ReadActionCache::class.java, ReadActionCacheImpl())
  ApplicationManager.setApplication(instance, rootDisposable)
  return instance
}

// Copy-pasted from IDEA (lack of this service causes PSI Scalar elements textValue calculation failure)
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

  fun clear() {
    threadProcessingContext.remove()
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