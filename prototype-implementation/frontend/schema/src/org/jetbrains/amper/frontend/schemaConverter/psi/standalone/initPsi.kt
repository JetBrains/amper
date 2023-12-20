package org.jetbrains.amper.frontend.schemaConverter.psi.standalone

import com.intellij.lang.*
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.ReadActionCache
import com.intellij.util.ProcessingContext
import com.intellij.util.messages.MessageBus
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.YAMLParserDefinition

fun initPsiFileFactory(rootDisposable: Disposable) {
  if (ApplicationManager.getApplication() != null) {
    // Init application and factory in standalone non-IDE environment only
    return
  }

  val application = initApplication(rootDisposable)
  val appContainer = application.picoContainer
  appContainer.registerComponentInstance(MessageBus::class.java, application.messageBus)

  LanguageParserDefinitions.INSTANCE.addExplicitExtension(YAMLLanguage.INSTANCE, YAMLParserDefinition())

  FileTypeRegistry.setInstanceSupplier {
    object: FileTypeRegistry() {
      override fun isFileIgnored(p0: VirtualFile): Boolean {
        TODO("Not yet implemented")
      }

      override fun getRegisteredFileTypes(): Array<FileType> {
        return arrayOf(YAMLFileType.YML)
      }

      override fun getFileTypeByFile(p0: VirtualFile): FileType {
        TODO("Not yet implemented")
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