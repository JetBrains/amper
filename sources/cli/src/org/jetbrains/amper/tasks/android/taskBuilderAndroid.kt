/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.SystemImageTags.GOOGLE_APIS_TAG
import com.android.sdklib.devices.Abi
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.FragmentTaskType
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.compose.ComposeFragmentTaskType
import org.jetbrains.amper.tasks.compose.isComposeEnabledFor
import org.jetbrains.amper.tasks.jvm.JvmClassesJarTask
import org.jetbrains.amper.tasks.jvm.JvmCompileTask
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import org.jetbrains.amper.tasks.jvm.JvmTestTask
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Path

fun ProjectTasksBuilder.setupAndroidTasks() {
    val androidSdkPath = context.androidHomeRoot.path

    allModules().alsoPlatforms(Platform.ANDROID).withEach {
        tasks.registerTask(
            CheckAndroidSdkLicenseTask(
                androidSdkPath,
                context.userCacheRoot,
                AndroidTaskType.CheckAndroidSdkLicense.getTaskName(module, Platform.ANDROID)
            ),
            AndroidTaskType.InstallCmdlineTools.getTaskName(module, Platform.ANDROID)
        )
        tasks.setupAndroidCommandlineTools(module, androidSdkPath, context.userCacheRoot)
    }

    allModules().alsoPlatforms(Platform.ANDROID)
        .alsoTests()
        .withEach {
            tasks.setupAndroidPlatformTask(module, androidSdkPath, context.userCacheRoot, isTest)
        }

    allModules().alsoPlatforms(Platform.ANDROID)
        .filterModuleType { it != ProductType.LIB }
        .alsoTests()
        .withEach {
            tasks.setupDownloadBuildToolsTask(module, androidSdkPath, context.userCacheRoot, isTest)
            tasks.setupDownloadPlatformToolsTask(module, androidSdkPath, context.userCacheRoot, isTest)
            tasks.setupDownloadSystemImageTask(module, androidSdkPath, context.userCacheRoot, isTest)
            tasks.registerTask(
                GetAndroidPlatformFileFromPackageTask(
                    "emulator",
                    androidSdkPath,
                    context.userCacheRoot,
                    AndroidTaskType.InstallEmulator.getTaskName(module, Platform.ANDROID, isTest)
                ),
                AndroidTaskType.CheckAndroidSdkLicense.getTaskName(module, Platform.ANDROID)
            )
        }

    allModules().alsoPlatforms(Platform.ANDROID)
        .filterModuleType { it != ProductType.LIB }
        .alsoTests()
        .alsoBuildTypes()
        .withEach {
            val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }

            tasks.setupPrepareAndroidTask(
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
                    CommonTaskType.Dependencies.getTaskName(module, platform, isTest),
                ),
                context.projectRoot,
                context.getTaskOutputPath(AndroidTaskType.Prepare.getTaskName(module, platform, isTest, buildType)),
                context.buildLogsRoot
            )

            tasks.setupAndroidBuildTask(
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

    allModules().alsoPlatforms(Platform.ANDROID)
        .alsoTests()
        .withEach {
            tasks.registerTask(
                TransformAarExternalDependenciesTask(
                    CommonTaskType.TransformDependencies.getTaskName(module, Platform.ANDROID, isTest),
                    executeOnChangedInputs
                ),
                CommonTaskType.Dependencies.getTaskName(module, Platform.ANDROID, isTest),
            )
        }

    allModules().alsoPlatforms(Platform.ANDROID)
        .alsoTests()
        .alsoBuildTypes()
        .withEach {
            val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }

            // compile
            val compileTaskName = CommonTaskType.Compile.getTaskName(module, platform, isTest, buildType)

            tasks.registerTask(
                JvmCompileTask(
                    module = module,
                    isTest = isTest,
                    fragments = fragments,
                    userCacheRoot = context.userCacheRoot,
                    projectRoot = context.projectRoot,
                    taskOutputRoot = context.getTaskOutputPath(compileTaskName),
                    taskName = compileTaskName,
                    executeOnChangedInputs = executeOnChangedInputs,
                    tempRoot = context.projectTempRoot,
                ),
                buildList {
                    add(AndroidTaskType.InstallPlatform.getTaskName(module, platform, isTest))
                    add(CommonTaskType.TransformDependencies.getTaskName(module, platform))
                    add(CommonTaskType.Dependencies.getTaskName(module, Platform.ANDROID, isTest))
                    if (module.type != ProductType.LIB) {
                        add(AndroidTaskType.Prepare.getTaskName(module, platform, isTest, buildType))
                    }
                }
            )

            val runtimeClasspathTaskName =
                CommonTaskType.RuntimeClasspath.getTaskName(module, platform, isTest, buildType)
            tasks.registerTask(
                JvmRuntimeClasspathTask(
                    module = module,
                    isTest = isTest,
                    taskName = runtimeClasspathTaskName,
                ),
                listOf(
                    CommonTaskType.Dependencies.getTaskName(module, Platform.ANDROID, isTest),
                )
            )

            if (isTest) {
                // Test depends directly on compilation output (classes)
                tasks.registerDependency(
                    taskName = runtimeClasspathTaskName,
                    dependsOn = CommonTaskType.Compile.getTaskName(module, platform, true, buildType)
                )
            } else {
                // Production always deals with JAR -> AAR, even for `ProductType.ANDROID_APP`
                val jarTaskName = CommonTaskType.Jar.getTaskName(module, platform, false, buildType)
                tasks.registerTask(
                    JvmClassesJarTask(
                        taskName = jarTaskName,
                        module = module,
                        isTest = false,
                        taskOutputRoot = context.getTaskOutputPath(jarTaskName),
                        executeOnChangedInputs = executeOnChangedInputs,
                    ),
                    CommonTaskType.Compile.getTaskName(module, platform, false, buildType),
                )

                val aarTaskName = AndroidTaskType.Aar.getTaskName(module, platform, false, buildType)
                tasks.registerTask(
                    AndroidAarTask(
                        taskName = aarTaskName,
                        executeOnChangedInputs = executeOnChangedInputs,
                        module = module,
                        taskOutputRoot = context.getTaskOutputPath(aarTaskName),
                        tempRoot = context.projectTempRoot,
                    ),
                    dependsOn = jarTaskName,
                )

                // Non-test runtime classpath depends on AAR, not JAR
                tasks.registerDependency(
                    taskName = runtimeClasspathTaskName,
                    dependsOn = aarTaskName,
                )

                if (isComposeEnabledFor(module)) {
                    fragments.forEach { fragment ->
                        tasks.registerDependency(
                            taskName = aarTaskName,
                            dependsOn = AndroidFragmentTaskType.PrepareComposeResources.getTaskName(fragment),
                        )
                    }
                }
            }
        }

    allModules().alsoPlatforms(Platform.ANDROID).withEach {
        if (isComposeEnabledFor(module)) {
            module.fragments.filter { Platform.ANDROID in it.platforms }.forEach { fragment ->
                val prepareTaskName = ComposeFragmentTaskType.ComposeResourcesPrepare.getTaskName(fragment)
                val prepareForAndroid = AndroidFragmentTaskType.PrepareComposeResources.getTaskName(fragment)
                tasks.registerTask(
                    task = AndroidComposeResourcesTask(
                        taskName = prepareForAndroid,
                    ),
                    dependsOn = prepareTaskName,
                )
            }
        }
    }

    allModules()
        .alsoPlatforms(Platform.ANDROID)
        .alsoTests()
        .alsoBuildTypes()
        .selectModuleDependencies(ResolutionScope.COMPILE).withEach {
            for (buildType in BuildType.entries) {
                tasks.registerDependency(
                    CommonTaskType.Compile.getTaskName(module, platform, isTest, buildType),
                    CommonTaskType.Compile.getTaskName(dependsOn, platform, false, buildType)
                )
            }
        }

    allModules()
        .alsoPlatforms(Platform.ANDROID)
        .alsoTests()
        .alsoBuildTypes()
        .selectModuleDependencies(ResolutionScope.RUNTIME).withEach {
            for (buildType in BuildType.entries) {
                val archiveTask = if (!isTest) {
                    // Non-test always depends on AAR for android module dependency
                    AndroidTaskType.Aar.getTaskName(dependsOn, platform, false, buildType)
                } else {
                    // Test always depends on a jar
                    CommonTaskType.Jar.getTaskName(dependsOn, platform, false, buildType)
                }
                tasks.registerDependency(
                    taskName = CommonTaskType.RuntimeClasspath.getTaskName(module, platform, isTest, buildType),
                    dependsOn = archiveTask,
                )
            }
        }

    allModules()
        .alsoPlatforms(Platform.ANDROID)
        .filterModuleType { it != ProductType.LIB }
        .alsoBuildTypes()
        .withEach {
            // run
            val runTaskName = CommonTaskType.Run.getTaskName(module, platform, false, buildType)
            tasks.registerTask(
                AndroidRunTask(
                    runTaskName,
                    module,
                    buildType,
                    androidSdkPath,
                    AndroidLocationsSingleton.avdLocation
                ),
                listOf(
                    AndroidTaskType.InstallSystemImage.getTaskName(module, platform, false),
                    AndroidTaskType.InstallEmulator.getTaskName(module, platform, false),
                    AndroidTaskType.Build.getTaskName(module, platform, false, buildType),
                )
            )
        }

    allModules()
        .alsoPlatforms(Platform.ANDROID)
        .alsoBuildTypes()
        .withEach {
            // test
            val testTaskName = CommonTaskType.Test.getTaskName(module, platform, true, buildType)
            tasks.registerTask(
                JvmTestTask(
                    module = module,
                    userCacheRoot = context.userCacheRoot,
                    projectRoot = context.projectRoot,
                    tempRoot = context.projectTempRoot,
                    taskName = testTaskName,
                    taskOutputRoot = context.getTaskOutputPath(testTaskName),
                    terminal = context.terminal,
                    platform = Platform.ANDROID,
                ),
                listOf(
                    CommonTaskType.Compile.getTaskName(module, platform, true, buildType),
                    CommonTaskType.RuntimeClasspath.getTaskName(module, platform, true, buildType),
                ),
            )

            tasks.registerDependency(
                taskName = CommonTaskType.Compile.getTaskName(module, platform, true, buildType),
                dependsOn = CommonTaskType.Compile.getTaskName(module, platform, false, buildType),
            )

            tasks.registerDependency(
                CommonTaskType.RuntimeClasspath.getTaskName(module, platform, true, buildType),
                CommonTaskType.Jar.getTaskName(module, platform, isTest = false, buildType = buildType)
            )
        }

    allModules()
        .alsoPlatforms(Platform.ANDROID)
        .withEach {
            val fragments = module.fragments.filter { !it.isTest && it.platforms.contains(Platform.ANDROID) }
            val taskName = AndroidTaskType.Bundle.getTaskName(module, Platform.ANDROID, false)
            tasks.registerTask(
                AndroidBundleTask(
                    module,
                    BuildType.Release,
                    executeOnChangedInputs,
                    androidSdkPath,
                    fragments,
                    context.projectRoot,
                    context.getTaskOutputPath(taskName),
                    context.buildLogsRoot,
                    taskName
                ),
                listOf(
                    CommonTaskType.RuntimeClasspath.getTaskName(module, Platform.ANDROID, false, BuildType.Release),
                )
            )
        }
}

private fun TaskGraphBuilder.setupAndroidPlatformTask(
    module: AmperModule,
    androidSdkPath: Path,
    userCacheRoot: AmperUserCacheRoot,
    isTest: Boolean,
) {
    val androidFragment = getAndroidFragment(module, isTest)
    val compileSdk = androidFragment?.settings?.android?.compileSdk?.versionNumber ?: return
    registerTask(
        GetAndroidPlatformJarTask(
            GetAndroidPlatformFileFromPackageTask(
                "platforms;android-$compileSdk",
                androidSdkPath,
                userCacheRoot,
                AndroidTaskType.InstallPlatform.getTaskName(module, Platform.ANDROID, isTest)
            )
        ),
        AndroidTaskType.CheckAndroidSdkLicense.getTaskName(module, Platform.ANDROID)
    )
}

private fun TaskGraphBuilder.setupDownloadBuildToolsTask(
    module: AmperModule,
    androidSdkPath: Path,
    userCacheRoot: AmperUserCacheRoot,
    isTest: Boolean,
) {
    val androidFragment = getAndroidFragment(module, isTest)
    registerTask(
        GetAndroidPlatformFileFromPackageTask(
            "build-tools;${androidFragment?.settings?.android?.compileSdk?.versionNumber}.0.0",
            androidSdkPath,
            userCacheRoot,
            AndroidTaskType.InstallBuildTools.getTaskName(module, Platform.ANDROID, isTest)
        ),
        AndroidTaskType.CheckAndroidSdkLicense.getTaskName(module, Platform.ANDROID)
    )
}


private fun TaskGraphBuilder.setupDownloadPlatformToolsTask(
    module: AmperModule,
    androidSdkPath: Path,
    userCacheRoot: AmperUserCacheRoot,
    isTest: Boolean,
) {
    registerTask(
        GetAndroidPlatformFileFromPackageTask(
            "platform-tools",
            androidSdkPath,
            userCacheRoot,
            AndroidTaskType.InstallPlatformTools.getTaskName(module, Platform.ANDROID, isTest)
        ),
        AndroidTaskType.CheckAndroidSdkLicense.getTaskName(module, Platform.ANDROID)
    )
}

private fun TaskGraphBuilder.setupDownloadSystemImageTask(
    module: AmperModule,
    androidSdkPath: Path,
    userCacheRoot: AmperUserCacheRoot,
    isTest: Boolean,
) {
    val androidFragment = getAndroidFragment(module, isTest)
    val versionNumber = androidFragment?.settings?.android?.targetSdk?.versionNumber ?: return
    val abi = if (DefaultSystemInfo.detect().arch == Arch.X64) Abi.X86_64 else Abi.ARM64_V8A
    registerTask(
        GetAndroidPlatformFileFromPackageTask(
            "system-images;android-$versionNumber;${GOOGLE_APIS_TAG.id};$abi",
            androidSdkPath,
            userCacheRoot,
            AndroidTaskType.InstallSystemImage.getTaskName(module, Platform.ANDROID, isTest)
        ),
        AndroidTaskType.CheckAndroidSdkLicense.getTaskName(module, Platform.ANDROID)
    )
}

private fun TaskGraphBuilder.setupPrepareAndroidTask(
    platform: Platform,
    module: AmperModule,
    isTest: Boolean,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    fragments: List<Fragment>,
    buildType: BuildType,
    androidSdkPath: Path,
    prepareAndroidTaskDependencies: List<TaskName>,
    projectRoot: AmperProjectRoot,
    taskOutputPath: TaskOutputRoot,
    buildLogsRoot: AmperBuildLogsRoot,
) {
    registerTask(
        AndroidPrepareTask(
            AndroidTaskType.Prepare.getTaskName(module, platform, isTest, buildType),
            module,
            buildType,
            executeOnChangedInputs,
            androidSdkPath,
            fragments,
            projectRoot,
            taskOutputPath,
            buildLogsRoot
        ),
        prepareAndroidTaskDependencies
    )
}


private fun TaskGraphBuilder.setupAndroidBuildTask(
    platform: Platform,
    module: AmperModule,
    isTest: Boolean,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    fragments: List<Fragment>,
    buildType: BuildType,
    androidSdkPath: Path,
    context: CliContext
) {
    val buildAndroidTaskName = AndroidTaskType.Build.getTaskName(module, platform, isTest, buildType)
    registerTask(
        AndroidBuildTask(
            module,
            buildType,
            executeOnChangedInputs,
            androidSdkPath,
            fragments,
            context.projectRoot,
            context.getTaskOutputPath(buildAndroidTaskName),
            context.buildLogsRoot,
            buildAndroidTaskName,
        ),
        listOf(
            CommonTaskType.RuntimeClasspath.getTaskName(module, platform, isTest, buildType)
        )
    )
}

private fun TaskGraphBuilder.setupAndroidCommandlineTools(
    module: AmperModule,
    androidSdkPath: Path,
    userCacheRoot: AmperUserCacheRoot
) {
    registerTask(
        GetAndroidPlatformFileFromPackageTask(
            "cmdline-tools;latest",
            androidSdkPath = androidSdkPath,
            userCacheRoot = userCacheRoot,
            AndroidTaskType.InstallCmdlineTools.getTaskName(module, Platform.ANDROID)
        )
    )
}

private fun getAndroidFragment(module: AmperModule, isTest: Boolean): LeafFragment? = module
    .fragments
    .filterIsInstance<LeafFragment>()
    .filter { it.isTest == isTest }.firstOrNull { Platform.ANDROID in it.platforms }

private enum class AndroidTaskType(override val prefix: String) : PlatformTaskType {
    InstallBuildTools("installBuildTools"),
    InstallPlatformTools("installPlatformTools"),
    InstallPlatform("installPlatform"),
    InstallSystemImage("installSystemImage"),
    InstallEmulator("installEmulator"),
    InstallCmdlineTools("installCmdlineTools"),
    CheckAndroidSdkLicense("checkAndroidSdkLicense"),
    Aar("aar"),
    Prepare("prepare"),
    Build("build"),
    Bundle("bundle"),
}

private enum class AndroidFragmentTaskType(override val prefix: String) : FragmentTaskType {
    PrepareComposeResources("prepareComposeResourcesForAndroid"),
}