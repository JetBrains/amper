package org.jetbrains.amper.frontend.schemaConverter.psi.standalone

import com.intellij.diagnostic.ActivityCategory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionsArea
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ExceptionUtilRt
import com.intellij.util.ReflectionUtil
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent
import java.lang.Deprecated

class DummyProject private constructor() : UserDataHolderBase(), Project {
  private object DummyProjectHolder {
    val ourInstance: DummyProject = DummyProject()
  }

  override fun getProjectFile(): VirtualFile? {
    return null
  }

  override fun getName(): String {
    return ""
  }

  override fun getLocationHash(): String {
    return "dummy"
  }

  override fun getProjectFilePath(): @SystemIndependent String? {
    return null
  }

  override fun getWorkspaceFile(): VirtualFile? {
    return null
  }

  override fun getBaseDir(): VirtualFile? {
    return null
  }

  override fun getBasePath(): @SystemIndependent String? {
    return null
  }

  override fun save() {}

  override fun <T> getService(serviceClass: Class<T>): T? {
    return null
  }

  override fun <T> getComponent(interfaceClass: Class<T>): T? {
    return null
  }

  override fun hasComponent(interfaceClass: Class<*>): Boolean {
    return false
  }

  override fun isInjectionForExtensionSupported(): Boolean {
    return false
  }

  override fun getExtensionArea(): ExtensionsArea {
    throw UnsupportedOperationException("getExtensionArea is not implement in : $javaClass")
  }

  override fun <T> instantiateClassWithConstructorInjection(
    aClass: Class<T>,
    key: Any,
    pluginId: PluginId
  ): T {
    throw UnsupportedOperationException()
  }

  override fun <T> instantiateClass(aClass: Class<T>, pluginId: PluginId): T {
    throw UnsupportedOperationException()
  }

  override fun isDisposed(): Boolean {
    return false
  }

  override fun getDisposed(): Condition<*> {
    return Condition { o: Any? -> isDisposed }
  }

  override fun isOpen(): Boolean {
    return false
  }

  override fun isInitialized(): Boolean {
    return false
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @ApiStatus.Internal
  override fun getCoroutineScope(): CoroutineScope {
    return GlobalScope
  }

  override fun getMessageBus(): MessageBus {
    return ApplicationManager.getApplication().messageBus
  }

  override fun dispose() {}

  @Throws(ClassNotFoundException::class)
  override fun <T> loadClass(className: String, pluginDescriptor: PluginDescriptor): Class<T> {
    return Class.forName(className) as Class<T>
  }

  override fun <T> instantiateClass(className: String, pluginDescriptor: PluginDescriptor): T & Any {
    try {
      return ReflectionUtil.newInstance(loadClass(className, pluginDescriptor))
    } catch (e: ClassNotFoundException) {
      throw RuntimeException(e)
    }
  }

  override fun getActivityCategory(isExtension: Boolean): ActivityCategory {
    return if (isExtension) ActivityCategory.PROJECT_EXTENSION else ActivityCategory.PROJECT_SERVICE
  }

  override fun createError(message: @NonNls String, pluginId: PluginId): RuntimeException {
    return RuntimeException(message)
  }

  override fun createError(
    message: @NonNls String,
    cause: Throwable?,
    pluginId: PluginId,
    attachments: Map<String, String>?
  ): RuntimeException {
    return RuntimeException(message)
  }

  override fun createError(error: Throwable, pluginId: PluginId): RuntimeException {
    ExceptionUtilRt.rethrowUnchecked(error)
    return RuntimeException(error)
  }

  companion object {
    val instance: Project
      get() = DummyProjectHolder.ourInstance
  }
}