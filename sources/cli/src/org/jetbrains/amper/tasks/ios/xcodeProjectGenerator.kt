/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import com.jetbrains.cidr.xcode.XcodeProjectId
import com.jetbrains.cidr.xcode.frameworks.ApplePlatform
import com.jetbrains.cidr.xcode.frameworks.AppleProductType
import com.jetbrains.cidr.xcode.frameworks.AppleSdkManager
import com.jetbrains.cidr.xcode.frameworks.buildSystem.BuildSettingNames
import com.jetbrains.cidr.xcode.model.PBXBuildPhase
import com.jetbrains.cidr.xcode.model.PBXProjectFile
import com.jetbrains.cidr.xcode.model.PBXProjectFileManipulator
import com.jetbrains.cidr.xcode.model.PBXReference
import com.jetbrains.cidr.xcode.plist.Plist
import com.jetbrains.cidr.xcode.plist.XMLPlistDriver
import com.jetbrains.cidr.xcode.util.XcodeUserDataHolder
import fleet.com.intellij.openapi.util.UserDataHolderEx
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.util.BuildType
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString


class XcodeProject : XcodeProjectId, UserDataHolderEx by XcodeUserDataHolder()

fun FileConventions.doGenerateBuildableXcodeproj(
    module: AmperModule,
    fragment: LeafFragment,
    buildOutputRoot: AmperBuildOutputRoot,
    targetName: String,
    productName: String,
    productBundleIdentifier: String,
    buildType: BuildType,
    appleSources: Set<File>,
): Path {
    val platform = fragment.platform
    val productModuleName = "iosApp"
    val applePlatform = AppleSdkManager.getInstance().findPlatformByType(fragment.platform)
        ?: error("Could not find SDK.")

    val commonSourceDir = appleSources.minBy { it.path.length }

    val projectFile = projectDir.resolve(PBXProjectFile.PROJECT_FILE)
        .createParentDirectories().run {
            // FIXME: We clean the old project, otherwise we have issues with consecutive builds.
            //  Investigate why that happens.
            deleteIfExists()
            createFile()
        }
    val pbxProjectFile = PBXProjectFileManipulator.createNewProject(
        XcodeProject(),
        baseDir,
        projectFile,
        null,
        null
    )
    val mainGroup = pbxProjectFile.projectObject.mainGroup!!

    with(pbxProjectFile.manipulator) {
        // Add configuration to the project.
        addConfiguration(buildType.variantName, emptyMap(), null)

        // Create plist.
        val targetPlist = createPlist(platform)
        val infoPlistFile = writePlist(baseDir, "Info-$targetName", targetPlist)
        addFile(infoPlistFile.pathString, emptyArray(), mainGroup, false)

        // Prepare settings.
        val settings = mutableMapOf<String, Any>(
            BuildSettingNames.INFOPLIST_FILE to infoPlistFile.relativeToBase().pathString,
            BuildSettingNames.PRODUCT_BUNDLE_IDENTIFIER to productBundleIdentifier,
            BuildSettingNames.ASSETCATALOG_COMPILER_APPICON_NAME to "AppIcon",
            // TODO Maybe add conditions?
            BuildSettingNames.TARGETED_DEVICE_FAMILY to listOf("1", "2"),
            BuildSettingNames.SDKROOT to applePlatform.type.platformName,
            BuildSettingNames.PRODUCT_NAME to productName,
            BuildSettingNames.PRODUCT_MODULE_NAME to productModuleName,
            BuildSettingNames.LD_RUNPATH_SEARCH_PATHS to "$(inherited) @executable_path/Frameworks @loader_path/Frameworks",
            BuildSettingNames.SWIFT_VERSION to "5.0",
            BuildSettingNames.ALWAYS_SEARCH_USER_PATHS to "NO",
            BuildSettingNames.CLANG_ENABLE_MODULES to "YES",
            BuildSettingNames.CLANG_ENABLE_OBJC_ARC to "YES"
        )

        // Order matters!
        // Create a source group and add sources.
        val sourcesGroup = addGroup(PBXReference.SOURCE_TREE_SOURCE_ROOT, productName, commonSourceDir.path)
        val pbxTarget = addNativeTarget(targetName, AppleProductType.APPLICATION_TYPE_ID, settings, applePlatform)
        pbxTarget.setAttribute("productName", productName)

        val relativeWrapperPath = checkNotNull(CliContext.wrapperScriptPath) { "Wrapper Script Path is not set" }
            .relativeToBase()
        addBuildPhase(
            PBXBuildPhase.Type.SHELL_SCRIPT,
            mapOf(
                "name" to "Build Kotlin with Amper",
                "shellPath" to "/bin/sh",
                "shellScript" to """
                    #!/bin/sh
                    # ----- AMPER KMP INTEGRATION STEP - GENERATED - DO NOT EDIT! ------
                    "${relativeWrapperPath.pathString}" tool xcode-integration --module="${module.userReadableName}"
                """.trimIndent(),
                "alwaysOutOfDate" to "1" //TODO: this disables dependency tracking, properly track in/outs?
            ),
            pbxTarget,
        )

        for (phaseType in listOf(
            PBXBuildPhase.Type.SOURCES,
            PBXBuildPhase.Type.FRAMEWORKS,
            PBXBuildPhase.Type.RESOURCES
        )) {
            addBuildPhase(phaseType, emptyMap(), pbxTarget)
        }
        val targetMemberships = arrayOf(pbxTarget)
        addTargetFiles(targetMemberships, sourcesGroup, appleSources)

        // Add build type dependant config.
        val variantSettings = settings.toMutableMap()
        if (buildType == BuildType.Debug) {
            variantSettings[BuildSettingNames.ONLY_ACTIVE_ARCH] = "YES"
            variantSettings[BuildSettingNames.ENABLE_TESTABILITY] = "YES"
            variantSettings[BuildSettingNames.SWIFT_OPTIMIZATION_LEVEL] = "-Onone"
            variantSettings[BuildSettingNames.GCC_OPTIMIZATION_LEVEL] = "0"
        }
        if (buildType == BuildType.Release) {
            variantSettings += fragment.settings.ios.teamId?.let { mapOf("DEVELOPMENT_TEAM" to it) } ?: emptyMap()
        }

        val frameworkSearchPath = IosConventions.Context(
            buildRootPath = buildOutputRoot.path,
            moduleName = module.userReadableName,
            buildType = BuildType.Debug,
            platform = platform,
        ).run {
            IosConventions.getAppFrameworkDirectory().relativeToBase().pathString
        }
        variantSettings.mergeListSetting(
            BuildSettingNames.FRAMEWORK_SEARCH_PATHS + "[arch=${fragment.platform.architecture}][sdk=${fragment.platform.sdk}*]",
            listOf(frameworkSearchPath),
        )

        addConfiguration(buildType.variantName, variantSettings, pbxTarget)
    }

    pbxProjectFile.save()

    return pbxProjectFile.xcodeProjFile
}

private fun writePlist(
    baseDir: Path,
    name: String,
    map: Plist,
): Path {
    val plist = Plist()
    plist["CFBundleDevelopmentRegion"] = "$(DEVELOPMENT_LANGUAGE)"
    plist["CFBundleExecutable"] = "$(EXECUTABLE_NAME)"
    plist["CFBundleIdentifier"] = "$(PRODUCT_BUNDLE_IDENTIFIER)"
    plist["CFBundleInfoDictionaryVersion"] = "6.0"
    plist["CFBundleName"] = "$(PRODUCT_NAME)"
    plist["CFBundlePackageType"] = "APPL"
    plist["CFBundleShortVersionString"] = "1.0"
    plist["CFBundleVersion"] = "1"
    plist += map

    val file = baseDir.resolve("$name.plist")
    XMLPlistDriver().write(plist, file.toFile())
    return file
}

private fun createPlist(
    platform: Platform,
): Plist {
    val plist = Plist()
    when (platform) {
        Platform.IOS, Platform.IOS_X64, Platform.IOS_ARM64, Platform.IOS_SIMULATOR_ARM64 -> {
            plist["UILaunchScreen"] = mapOf<String, Any>().toPlist()

            // Needed for https://github.com/JetBrains/compose-multiplatform/issues/3634
            // TODO: Maybe don't force it here
            //  when we migrate to the external user-maintained xcode project way.
            plist["CADisableMinimumFrameDurationOnPhone"] = true
        }

        else -> Unit
    }
    return plist
}

fun AppleSdkManager.findPlatformByType(platform: Platform) = when (platform) {
    Platform.TVOS_ARM64,
    Platform.TVOS_X64 -> findPlatformByType(ApplePlatform.Type.TVOS)

    Platform.TVOS_SIMULATOR_ARM64 -> findPlatformByType(ApplePlatform.Type.TVOS_SIMULATOR)

    Platform.MACOS_X64,
    Platform.MACOS_ARM64 -> findPlatformByType(ApplePlatform.Type.MACOS)

    Platform.IOS_ARM64,
    Platform.IOS_X64 -> findPlatformByType(ApplePlatform.Type.IOS)

    Platform.IOS_SIMULATOR_ARM64 -> findPlatformByType(ApplePlatform.Type.IOS_SIMULATOR)

    Platform.WATCHOS_ARM64,
    Platform.WATCHOS_ARM32,
    Platform.WATCHOS_DEVICE_ARM64 -> findPlatformByType(ApplePlatform.Type.WATCH)

    Platform.WATCHOS_SIMULATOR_ARM64 -> findPlatformByType(ApplePlatform.Type.WATCH_SIMULATOR)

    else -> error("Should not be here, since allowed platforms are only apple related")
}