/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.devices.Abi
import com.android.sdklib.repository.targets.SystemImage.DEFAULT_TAG
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.jvm.JvmCompileTask
import org.jetbrains.amper.tasks.jvm.JvmTestTask
import org.jetbrains.amper.tasks.ProjectTaskRegistrar
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.util.AndroidSdkDetector
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Path

private val androidSdkPath by lazy { AndroidSdkDetector().detectSdkPath() ?: error("Android SDK is not found") }

fun ProjectTaskRegistrar.setupAndroidTasks() {

    onEachDescendantPlatformOf(Platform.ANDROID) { module, _, _ ->
        registerTask(
            LogcatTask(TaskName.fromHierarchy(listOf(module.userReadableName, "logcat"))),
            CommonTaskType.Run.getTaskName(module, Platform.ANDROID, isTest = false, BuildType.Debug)
        )
    }

    onEachTaskType(Platform.ANDROID) { module, executeOnChangedInputs, _, isTest ->
        registerTask(
            TransformAarExternalDependenciesTask(
                AndroidTaskType.TransformDependencies.getTaskName(module, Platform.ANDROID, isTest),
                executeOnChangedInputs
            ),
            CommonTaskType.Dependencies.getTaskName(module, Platform.ANDROID, isTest),
        )

        setupAndroidPlatformTask(module, executeOnChangedInputs, androidSdkPath, isTest)
        setupDownloadBuildToolsTask(module, executeOnChangedInputs, androidSdkPath, isTest)
        setupDownloadPlatformToolsTask(module, executeOnChangedInputs, androidSdkPath, isTest)
        setupDownloadSystemImageTask(module, executeOnChangedInputs, isTest)
        registerTask(
            GetAndroidPlatformFileFromPackageTask(
                "emulator",
                executeOnChangedInputs,
                androidSdkPath,
                AndroidTaskType.InstallEmulator.getTaskName(module, Platform.ANDROID, isTest)
            )
        )
    }

    onEachBuildType(Platform.ANDROID) { module, executeOnChangedInputs, platform, isTest, buildType ->
        val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }
        setupPrepareAndroidTask(
            platform,
            module,
            isTest,
            executeOnChangedInputs,
            fragments,
            buildType,
            androidSdkPath,
            listOf(
                AndroidTaskType.InstallBuildTools.getTaskName(module, platform, isTest),
                AndroidTaskType.InstallPlatformTools.getTaskName(module, platform, isTest),
                AndroidTaskType.InstallPlatform.getTaskName(module, platform, isTest),
                CommonTaskType.Dependencies.getTaskName(module, platform, isTest)
            ),
            context.getTaskOutputPath(AndroidTaskType.Prepare.getTaskName(module, platform, isTest, buildType))
        )

        // compile

        val compileTaskName = CommonTaskType.Compile.getTaskName(module, platform, isTest, buildType)

        registerTask(
            JvmCompileTask(
                module = module,
                isTest = isTest,
                fragments = fragments,
                userCacheRoot = context.userCacheRoot,
                projectRoot = context.projectRoot,
                taskOutputRoot = context.getTaskOutputPath(compileTaskName),
                taskName = compileTaskName,
                executeOnChangedInputs = executeOnChangedInputs,
            ),
            listOf(
                AndroidTaskType.TransformDependencies.getTaskName(module, platform),
                AndroidTaskType.InstallPlatform.getTaskName(module, platform, isTest),
                AndroidTaskType.Prepare.getTaskName(module, platform, isTest, buildType),
                // crutch because dependencies couldn't be transitive
                CommonTaskType.Dependencies.getTaskName(module, Platform.ANDROID, isTest)
            )
        )

        setupAndroidBuildTasks(
            platform,
            module,
            isTest,
            executeOnChangedInputs,
            fragments,
            buildType,
            androidSdkPath,
            context
        )
    }

    onCompileModuleDependency(Platform.ANDROID) { module, dependsOn, _, platform, isTest, buildType ->
        registerDependency(
            CommonTaskType.Compile.getTaskName(module, platform, isTest, buildType),
            CommonTaskType.Compile.getTaskName(dependsOn, platform, false, buildType)
        )
    }

    onRuntimeModuleDependency(Platform.ANDROID) { module, dependsOn, _, platform, isTest, buildType ->
        registerDependency(
            AndroidTaskType.Build.getTaskName(module, platform, isTest, buildType),
            CommonTaskType.Compile.getTaskName(dependsOn, platform, false, buildType)
        )
    }

    onMain(Platform.ANDROID) { module, _, platform, isTest, buildType ->
        // run
        val runTaskName = CommonTaskType.Run.getTaskName(module, platform, isTest, buildType)
        registerTask(
            AndroidRunTask(
                runTaskName,
                module,
                buildType,
                androidSdkPath,
                AndroidLocationsSingleton.avdLocation
            ),
            listOf(
                AndroidTaskType.InstallSystemImage.getTaskName(module, platform, isTest),
                AndroidTaskType.InstallEmulator.getTaskName(module, platform, isTest),
                AndroidTaskType.Build.getTaskName(module, platform, isTest, buildType),
            )
        )
    }

    onTest(Platform.ANDROID) {module, _, platform, isTest, buildType ->
        // test
        val testTaskName = CommonTaskType.Test.getTaskName(module, platform, isTest, buildType)
        registerTask(
            JvmTestTask(
                module = module,
                userCacheRoot = context.userCacheRoot,
                projectRoot = context.projectRoot,
                taskName = testTaskName,
                taskOutputRoot = context.getTaskOutputPath(testTaskName),
            ),
            CommonTaskType.Compile.getTaskName(module, platform, isTest, buildType)
        )

        registerDependency(
            taskName = CommonTaskType.Compile.getTaskName(module, platform, true, buildType),
            dependsOn = CommonTaskType.Compile.getTaskName(module, platform, false, buildType),
        )
    }
}

private fun TaskGraphBuilder.setupAndroidPlatformTask(
    module: PotatoModule,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    androidSdkPath: Path,
    isTest: Boolean,
) {
    val androidFragment = getAndroidFragment(module, isTest)
    val targetSdk = androidFragment?.settings?.android?.targetSdk?.versionNumber ?: 34
    registerTask(
        GetAndroidPlatformJarTask(
            GetAndroidPlatformFileFromPackageTask(
                "platforms;android-$targetSdk",
                executeOnChangedInputs,
                androidSdkPath,
                AndroidTaskType.InstallPlatform.getTaskName(module, Platform.ANDROID, isTest)
            )
        )
    )
}

private fun TaskGraphBuilder.setupDownloadBuildToolsTask(
    module: PotatoModule,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    androidSdkPath: Path,
    isTest: Boolean,
) {
    val androidFragment = getAndroidFragment(module, isTest)
    registerTask(
        GetAndroidPlatformFileFromPackageTask(
            "build-tools;${androidFragment?.settings?.android?.targetSdk?.versionNumber}.0.0",
            executeOnChangedInputs,
            androidSdkPath,
            AndroidTaskType.InstallBuildTools.getTaskName(module, Platform.ANDROID, isTest)
        )
    )
}


private fun TaskGraphBuilder.setupDownloadPlatformToolsTask(
    module: PotatoModule,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    androidSdkPath: Path,
    isTest: Boolean,
) {
    registerTask(
        GetAndroidPlatformFileFromPackageTask(
            "platform-tools",
            executeOnChangedInputs,
            androidSdkPath,
            AndroidTaskType.InstallPlatformTools.getTaskName(module, Platform.ANDROID, isTest)
        )
    )
}

private fun TaskGraphBuilder.setupDownloadSystemImageTask(
    module: PotatoModule,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    isTest: Boolean,
) {
    val androidFragment = getAndroidFragment(module, isTest)
    val versionNumber = androidFragment?.settings?.android?.targetSdk?.versionNumber ?: 34
    val abi = if (DefaultSystemInfo.detect().arch == SystemInfo.Arch.X64) Abi.X86_64 else Abi.ARM64_V8A
    registerTask(
        GetAndroidPlatformFileFromPackageTask(
            "system-images;android-$versionNumber;${DEFAULT_TAG.id};$abi",
            executeOnChangedInputs,
            androidSdkPath,
            AndroidTaskType.InstallSystemImage.getTaskName(module, Platform.ANDROID, isTest)
        )
    )
}

private fun TaskGraphBuilder.setupPrepareAndroidTask(
    platform: Platform,
    module: PotatoModule,
    isTest: Boolean,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    fragments: List<Fragment>,
    buildType: BuildType,
    androidSdkPath: Path,
    prepareAndroidTaskDependencies: List<TaskName>,
    taskOutputPath: TaskOutputRoot,
) {
    registerTask(
        AndroidPrepareTask(
            AndroidTaskType.Prepare.getTaskName(module, platform, isTest, buildType),
            module,
            buildType,
            executeOnChangedInputs,
            androidSdkPath,
            fragments,
            taskOutputPath
        ),
        prepareAndroidTaskDependencies
    )
}


private fun TaskGraphBuilder.setupAndroidBuildTasks(
    platform: Platform,
    module: PotatoModule,
    isTest: Boolean,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    fragments: List<Fragment>,
    buildType: BuildType,
    androidSdkPath: Path,
    context: ProjectContext
) {
    val buildAndroidTaskName = AndroidTaskType.Build.getTaskName(module, platform, isTest, buildType)
    registerTask(
        AndroidBuildTask(
            module,
            buildType,
            executeOnChangedInputs,
            androidSdkPath,
            fragments,
            context.getTaskOutputPath(buildAndroidTaskName),
            buildAndroidTaskName,
        ),
        listOf(
            CommonTaskType.Dependencies.getTaskName(module, platform, isTest),
            CommonTaskType.Compile.getTaskName(module, platform, isTest, buildType)
        )
    )
}

private fun getAndroidFragment(module: PotatoModule, isTest: Boolean): LeafFragment? = module
    .fragments
    .filterIsInstance<LeafFragment>()
    .filter { it.isTest == isTest }.firstOrNull { Platform.ANDROID in it.platforms }

private enum class AndroidTaskType(override val prefix: String) : PlatformTaskType {
    InstallBuildTools("installBuildTools"),
    InstallPlatformTools("installPlatformTools"),
    InstallPlatform("installPlatform"),
    InstallSystemImage("installSystemImage"),
    InstallEmulator("installEmulator"),
    TransformDependencies("transformDependencies"),
    Prepare("prepare"),
    Build("build")
}
