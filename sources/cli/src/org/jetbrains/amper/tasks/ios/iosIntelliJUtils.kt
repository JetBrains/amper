/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import com.jetbrains.apple.sdk.AppleSdkManagerBase
import com.jetbrains.cidr.xcode.XcodeBase
import com.jetbrains.cidr.xcode.XcodeComponentManager
import com.jetbrains.cidr.xcode.XcodeProjectId
import com.jetbrains.cidr.xcode.XcodeSettingsBase
import com.jetbrains.cidr.xcode.cache.CachedValuesManager
import com.jetbrains.cidr.xcode.cache.CachedValuesManagerImpl
import com.jetbrains.cidr.xcode.frameworks.AppleFileTypeManager
import com.jetbrains.cidr.xcode.frameworks.AppleSdkManager
import com.jetbrains.cidr.xcode.model.CoreXcodeWorkspace
import com.jetbrains.cidr.xcode.model.XcodeProjectTrackers
import com.jetbrains.cidr.xcode.xcspec.XcodeExtensionsManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import java.util.concurrent.atomic.AtomicBoolean

private val xCodeComponentInitializationCalled = AtomicBoolean(false)
private val xCodeInitializationMutex = Mutex()

/**
 * Initializes the Xcode component manager once.
 */
suspend fun initializeXcodeComponentManager() = xCodeInitializationMutex.withLock {
    if (DefaultSystemInfo.detect().family != OsFamily.MacOs)
        userReadableError("This task is supported only on macOS systems.")

    if (xCodeComponentInitializationCalled.getAndSet(true)) return@withLock

    val xcodePath = detectXcodeInstallation()
    StandaloneXcodeComponentManager.registerManager(xcodePath)
}

private suspend fun detectXcodeInstallation(): String {
    val result = runProcessAndCaptureOutput(
        command = listOf("xcode-select", "--print-path"),
        outputListener = ProcessOutputListener.NOOP,
    )
    if (result.exitCode != 0) userReadableError("Failed to detect Xcode. Make sure Xcode is installed.")

    return result.stdout.trim()
}

private class StandaloneXcodeComponentManager(private val xcodePath: String) : XcodeComponentManager {
    override fun isUnitTestMode(): Boolean = false

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
                else -> throw IllegalArgumentException("Unknown service class is requested from XcodeComponentManager: ${clazz.name}")
            }
        }.let(clazz::cast)
    }

    override fun <T : Any> getExtensions(ep: XcodeComponentManager.EP<T>): List<T> {
        return emptyList()
    }

    companion object {
        fun registerManager(xcodePath: String) {
            XcodeComponentManager.registerImpl(object : XcodeComponentManager.Initializer {
                private val appMan = StandaloneXcodeComponentManager(xcodePath)
                private val proMan = StandaloneXcodeComponentManager(xcodePath)

                override val applicationManager: XcodeComponentManager
                    get() = appMan

                override fun getProjectManager(projectId: XcodeProjectId): XcodeComponentManager {
                    return proMan
                }
            })
        }
    }
}
