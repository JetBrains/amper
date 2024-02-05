/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.NullOutputReceiver
import com.android.prefs.AndroidLocationsSingleton
import com.android.repository.api.ConsoleProgressIndicator
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.utils.StdLogger
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.readText

class AndroidRunTask(
    override val taskName: TaskName,
    override val module: PotatoModule,
    private val androidSdkPath: Path,
    private val avdPath: Path
) : RunTask {
    override val platform: Platform
        get() = Platform.ANDROID

    private val fragments = module.fragments.filter { !it.isTest && it.platforms.contains(platform) }

    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        AndroidDebugBridge.init(true)
        val adb = AndroidDebugBridge.getBridge() ?: AndroidDebugBridge.createBridge(androidSdkPath.resolve("platform-tools/adb").toString(), false)
        adb.waitForConnection()
        val androidFragment = fragments.singleOrNull() ?: error("Only one $platform fragment is expected")

        val emulatorExecutable = (dependenciesResult
            .filterIsInstance<GetAndroidPlatformFileFromPackageTask.TaskResult>()
            .flatMap { it.outputs.filter { it.endsWith("emulator") } }.singleOrNull()
            ?: error("Emulator not found")).resolve("emulator")

        val device = adb
            .selectOrCreateVirtualDevice(androidFragment.settings.android.targetSdk.versionNumber, emulatorExecutable)

        val apk = dependenciesResult.filterIsInstance<AndroidBuildTask.TaskResult>()
            .singleOrNull()?.artifacts?.firstOrNull() ?: error("Apk not found")
        device.installPackage(apk.pathString, true)

        val activityName = findActivityToLaunch(androidFragment) ?: error("Could not find activity to launch")

        device.executeShellCommand(
            "am start -a android.intent.action.MAIN -n ${androidFragment.settings.android.namespace}/$activityName",
            NullOutputReceiver()
        )

        return TaskResult(dependenciesResult)
    }

    private suspend fun AndroidDebugBridge.waitForConnection() {
        while (!hasInitialDeviceList()) {
            yield()
        }
    }

    private fun findActivityToLaunch(androidFragment: Fragment): String? =
        parseManifest(androidFragment.src.resolve("AndroidManifest.xml"))
            .application
            .activities
            .firstOrNull {
                val isMain = it.intentFilters.any { it.action.name == "android.intent.action.MAIN" }
                val isLauncher = it.intentFilters.any { it.category.name == "android.intent.category.LAUNCHER" }
                isMain && isLauncher
            }
            ?.name

    private fun parseManifest(manifestPath: Path) = xml.decodeFromString<AndroidManifest>(manifestPath.readText())

    private suspend fun AndroidDebugBridge.selectOrCreateVirtualDevice(
        androidTarget: Int,
        emulatorExecutable: Path
    ): IDevice = coroutineScope {
        val androidVersion = AndroidVersion(androidTarget)
        val selectedDevice = devices.firstOrNull { it.version.canRun(androidVersion) }
        selectedDevice ?: run {
            val sdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton, androidSdkPath)
            val consoleProgressIndicator = ConsoleProgressIndicator()
            val systemImageManager = sdkHandler.getSystemImageManager(consoleProgressIndicator)
            val systemImage = systemImageManager.images.firstOrNull { it.androidVersion.canRun(androidVersion) }
                ?: error("System image for $androidVersion not found")
            val deviceManager = DeviceManager.createInstance(sdkHandler, StdLogger(StdLogger.Level.VERBOSE))
            val avdManager =
                AvdManager.createInstance(sdkHandler, avdPath, deviceManager, StdLogger(StdLogger.Level.VERBOSE))
            val avd = avdManager
                .validAvds
                .firstOrNull { it.androidVersion.canRun(androidVersion) }
                ?: run {
                    // create a new one
                    avdManager.createAvd(
                        avdPath,
                        "amper-$androidTarget",
                        systemImage,
                        null,
                        null,
                        null,
                        mutableMapOf(),
                        mutableMapOf(),
                        true,
                        true,
                        true
                    )
                }

            runAndAwaitForEmulatorToBoot(emulatorExecutable, avd.name)
            waitForDevice(androidVersion)
        }
    }

    private suspend fun runAndAwaitForEmulatorToBoot(emulatorExecutable: Path, avdName: String) {
        val ready = atomic(false)
        BuildPrimitives.fireProcessAndForget(
            listOf(
                emulatorExecutable.pathString,
                "-avd",
                avdName
            ),
            emulatorExecutable.parent,
            environment = mapOf(
                "ANDROID_AVD_HOME" to avdPath.toString(),
                "ANDROID_HOME" to androidSdkPath.toString(),
                "ANDROID_SDK_ROOT" to androidSdkPath.toString()
            ),
            onStdoutLine = {
                println(it)
                if (it.contains("Boot completed") || it.contains("Successfully loaded snapshot")) {
                    ready.update { true }
                }
            }
        )

        while (!ready.value) {
            yield()
        }
    }

    private suspend fun AndroidDebugBridge.waitForDevice(targetVersion: AndroidVersion): IDevice {
        var device: IDevice?
        do {
            yield()
            device = devices.firstOrNull { it.version.canRun(targetVersion) && it.isOnline }
        } while (device == null)
        return device
    }

    data class TaskResult(override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>) :
        org.jetbrains.amper.tasks.TaskResult
}

internal val xml = XML {
    defaultPolicy {
        ignoreUnknownChildren()
        repairNamespaces = false
    }
}

internal const val namespace = "http://schemas.android.com/apk/res/android"
internal const val prefix = "android"

@Serializable
@XmlSerialName("manifest")
data class AndroidManifest(@XmlElement(true) val application: Application) {
    @Serializable
    @XmlSerialName("application")
    data class Application(
        @XmlElement(true)
        @XmlSerialName("activity")
        val activities: List<Activity>
    ) {
        @Serializable
        @XmlSerialName("activity")
        data class Activity(
            @XmlSerialName("name", namespace, prefix)
            val name: String,
            @XmlElement(true)
            @XmlSerialName("intent-filter")
            val intentFilters: List<IntentFilter>
        ) {
            @Serializable
            @XmlSerialName("intent-filter")
            data class IntentFilter(
                @XmlElement(true)
                @XmlSerialName("action")
                val action: Action,
                @XmlElement(true)
                @XmlSerialName("category")
                val category: Category
            ) {
                @Serializable
                @XmlSerialName("action")
                data class Action(
                    @XmlSerialName("name", namespace, prefix)
                    val name: String
                )

                @Serializable
                @XmlSerialName("category")
                data class Category(
                    @XmlSerialName("name", namespace, prefix)
                    val name: String
                )
            }
        }
    }
}
