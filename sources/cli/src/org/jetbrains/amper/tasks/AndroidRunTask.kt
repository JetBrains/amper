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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.headlessEmulatorModePropertyName
import org.jetbrains.amper.util.startProcessWithStdoutStderrFlows
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.readText

class AndroidRunTask(
    override val taskName: TaskName,
    override val module: PotatoModule,
    override val buildType: BuildType,
    private val androidSdkPath: Path,
    private val avdPath: Path,
) : RunTask {
    override val platform: Platform
        get() = Platform.ANDROID

    private val fragments = module.fragments.filter { !it.isTest && it.platforms.contains(platform) }

    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        val adb = waitForAdbConnection()
        val androidFragment = fragments.singleOrNull() ?: error("Only one $platform fragment is expected")

        val emulatorExecutable = (dependenciesResult
            .filterIsInstance<GetAndroidPlatformFileFromPackageTask.TaskResult>()
            .flatMap { it.outputs.filter { it.endsWith("emulator") } }.singleOrNull()
            ?: error("Emulator not found")).resolve("emulator")

        val device = adb
            .selectOrCreateVirtualDevice(androidFragment.settings.android.targetSdk.versionNumber, emulatorExecutable)

        val apk = dependenciesResult.filterIsInstance<AndroidBuildTask.TaskResult>()
            .singleOrNull()?.artifacts?.firstOrNull() ?: error("Apk not found")
        device.installPackage(apk.pathString, true, "--bypass-low-target-sdk-block")

        val activityName = findActivityToLaunch(androidFragment) ?: error("Could not find activity to launch")

        device.executeShellCommand(
            "am start -a android.intent.action.MAIN -n ${androidFragment.settings.android.namespace}/$activityName",
            NullOutputReceiver()
        )

        return TaskResult(dependenciesResult, device)
    }

    /**
     * Wait for adb connection
     *
     * Polling is the only way to wait for ADB connection
     *
     * And we need not only wait until adb is connected,
     * but devices list first time initialized, otherwise the device's request right after connection returns an empty
     * array
     */
    private suspend fun waitForAdbConnection(): AndroidDebugBridge {
        AndroidDebugBridge.init(true)
        val adb = AndroidDebugBridge.getBridge()
            ?: AndroidDebugBridge.createBridge(androidSdkPath.resolve("platform-tools/adb").toString(), false)
        flow {
            while(!adb.hasInitialDeviceList()) {
                emit(false)
                delay(100)
            }
            emit(true)
        }.first { it }
        return adb
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
    ): IDevice {
        val androidVersion = AndroidVersion(androidTarget)
        val selectedDevice = devices.firstOrNull { it.version.canRun(androidVersion) }
        return selectedDevice ?: run {
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
                        avdPath.resolve("amper-$androidTarget.avd"),
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
        startProcessWithStdoutStderrFlows(
            buildList {
                add(emulatorExecutable.pathString)
                val headlessMode: String? = System.getProperty(headlessEmulatorModePropertyName)
                if (headlessMode == "true") {
                    add("-no-window")
                }
                add("-avd")
                add(avdName)
            },
            emulatorExecutable.parent,
            environment = mapOf(
                "ANDROID_AVD_HOME" to avdPath.toString(),
                "ANDROID_HOME" to androidSdkPath.toString(),
            )
        )
            .stdout
            .map { it.also { logger.info(it) } }
            // we need to wait until the boot will be completed (not until a device becomes online) because when becomes
            // online installation service isn't available yet
            .first { (it.contains("Boot completed") || it.contains("Successfully loaded snapshot")) }
    }

    private suspend fun waitForDevice(targetVersion: AndroidVersion): IDevice {
        val deferred = CompletableDeferred<IDevice>()
        val listener = object : AndroidDebugBridge.IDeviceChangeListener {
            override fun deviceConnected(device: IDevice) {
            }

            override fun deviceDisconnected(device: IDevice?) {
            }

            override fun deviceChanged(device: IDevice, changeMask: Int) {
                if (device.state == IDevice.DeviceState.ONLINE && device.version.canRun(targetVersion)) {
                    deferred.complete(device)
                }
            }
        }
        AndroidDebugBridge.addDeviceChangeListener(listener)
        val device = deferred.await()
        AndroidDebugBridge.removeDeviceChangeListener(listener)
        return device
    }

    data class TaskResult(override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>, val device: IDevice) :
        org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
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
