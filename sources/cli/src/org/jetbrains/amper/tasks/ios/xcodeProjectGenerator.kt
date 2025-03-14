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
import com.jetbrains.cidr.xcode.model.PBXCopyFilesBuildPhase
import com.jetbrains.cidr.xcode.model.PBXDictionary
import com.jetbrains.cidr.xcode.model.PBXProjectFile
import com.jetbrains.cidr.xcode.model.PBXProjectFileManipulator
import com.jetbrains.cidr.xcode.model.PBXReference
import com.jetbrains.cidr.xcode.model.PBXTarget
import com.jetbrains.cidr.xcode.plist.Plist
import com.jetbrains.cidr.xcode.plist.XMLPlistDriver
import com.jetbrains.cidr.xcode.util.XcodeUserDataHolder
import fleet.com.intellij.openapi.util.UserDataHolderEx
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.tasks.compose.isComposeEnabledFor
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
    frameworkDependencies: List<File>,
): Path {
    val platform = fragment.platform
    val productModuleName = "iosApp"
    val applePlatform = AppleSdkManager.getInstance().findPlatformByType(fragment.platform)
        ?: error("Could not find SDK.")

    val commonSourceDir = appleSources.minBy { it.path.length }

    val projectFile = projectDir.resolve("project.pbxproj").toPath()
        .createParentDirectories().run {
            // FIXME: We clean the old project, otherwise we have issues with consecutive builds.
            //  Investigate why that happens.
            deleteIfExists()
            createFile()
        }
    val pbxProjectFile = PBXProjectFileManipulator.createNewProject(
        XcodeProject(),
        baseDir.toPath(),
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
        addFile(infoPlistFile.path, emptyArray(), mainGroup, false)

        // Prepare settings.
        val settings = mutableMapOf<String, Any>(
            BuildSettingNames.INFOPLIST_FILE to infoPlistFile.relativeToBase().path,
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
        if (frameworkDependencies.isNotEmpty()) {
            val frameworkSearchPaths = frameworkDependencies.map { it.parentFile.relativeToBase().path }
            variantSettings.mergeListSetting(
                BuildSettingNames.FRAMEWORK_SEARCH_PATHS + "[arch=${fragment.platform.architecture}][sdk=${fragment.platform.sdk}*]",
                frameworkSearchPaths.toList()
            )
        }
        addConfiguration(buildType.variantName, variantSettings, pbxTarget)

        addFrameworksStages(
            this,
            frameworkDependencies,
            buildType,
            fragment,
            pbxTarget,
            targetMemberships,
            pbxProjectFile
        )

        addResourcesStages(
            module = module,
            leafFragment = fragment,
            outputRoot = buildOutputRoot,
            manipulator = this,
            pbxTarget = pbxTarget,
        )
    }

    pbxProjectFile.save()

    return pbxProjectFile.xcodeProjFile
}

private fun addResourcesStages(
    module: AmperModule,
    leafFragment: LeafFragment,
    outputRoot: AmperBuildOutputRoot,
    manipulator: PBXProjectFileManipulator,
    pbxTarget: PBXTarget,
) {
    if (isComposeEnabledFor(module)) {
        val resourcesConventionDir = IosComposeResourcesTask.resourcesConventionDirectory(outputRoot, leafFragment)
        // `compose-resources/` is a content path where the "resources" library expects to find the files.
        val destination = "\$BUILT_PRODUCTS_DIR/\$CONTENTS_FOLDER_PATH/compose-resources"
        val copyResourcesScript = buildString {
            appendLine("#!/bin/sh/")
            // TODO: check arch, conf, platform?
            // TODO: Maybe replace with directory symlink
            //  or a tree of links depending on whether xcode follows symlinks while traversing the directory
            appendLine("if [[ -d \"${resourcesConventionDir.pathString}\" ]]; then")
            appendLine("  mkdir -p \"$destination\"")
            appendLine("  cp -r \"${resourcesConventionDir.pathString}\"/* \"$destination\"")
            appendLine("fi")
        }
        manipulator.addBuildPhase(
            PBXBuildPhase.Type.SHELL_SCRIPT,
            mapOf(
                "name" to "Stage Resources",
                "shellPath" to "/bin/sh",
                "shellScript" to copyResourcesScript,
                "alwaysOutOfDate" to "1",
            ),
            pbxTarget,
        ).setAttribute("outputPaths", listOf(destination))
    }
}

/**
 * Adding stages, responsible for including frameworks into the resulting bundle.
 */
private fun FileConventions.addFrameworksStages(
    manipulator: PBXProjectFileManipulator,
    frameworkDependencies: List<File>,
    buildType: BuildType,
    fragment: LeafFragment,
    pbxTarget: PBXTarget,
    targetMemberships: Array<PBXTarget>,
    pbxProjectFile: PBXProjectFile,
) {
    val frameworksGroup = manipulator.addGroup(PBXReference.SOURCE_TREE_GROUP, "frameworks", null)
    val embeddedFrameworks = mutableSetOf<String>()
    val buildPhaseOutputPaths = mutableSetOf<String>()
    var copyFrameworksScript = "#!/bin/sh\nmkdir -p \"\$SRCROOT/${frameworksStagingPathString}\"\n"

    if (frameworkDependencies.isNotEmpty()) {
        copyFrameworksScript += "if [ \"\$CONFIGURATION\" = \"${buildType.variantName}\" ] && [ \"\$ARCHS\" = \"${fragment.platform.architecture}\" ] && [ \"\$PLATFORM_NAME\" = \"${fragment.platform.sdk}\" ]; then\n"
        for (framework in frameworkDependencies) {
            embeddedFrameworks.add(framework.name)
            val symlinkPath = "\$SRCROOT/${frameworksStagingPathString}/${framework.name}"
            buildPhaseOutputPaths.add(symlinkPath)
            copyFrameworksScript += "    ln -sfn \"\$SRCROOT/${framework.relativeToBase().path}\" \"${symlinkPath}\"\n"
        }
        copyFrameworksScript += "fi\n"
    }

    manipulator.addBuildPhase(
        PBXBuildPhase.Type.SHELL_SCRIPT,
        mapOf(
            "name" to "Stage Frameworks",
            "shellPath" to "/bin/sh",
            "shellScript" to copyFrameworksScript,
            "alwaysOutOfDate" to "1" //TODO: this disables dependency tracking, properly track in/outs?
        ),
        pbxTarget
    ).setAttribute("outputPaths", buildPhaseOutputPaths.toList())

    val embedFrameworksPhase = manipulator.addBuildPhase(
        PBXBuildPhase.Type.COPY_FILES, mapOf(
            "name" to "Embed Frameworks",
            "dstSubfolderSpec" to PBXCopyFilesBuildPhase.DestinationType.FRAMEWORKS.spec
        ), pbxTarget
    )

    val embeddedFrameworkPaths = embeddedFrameworks.map { frameworksStagingDir.resolve(it).path }
    for (embeddedFrameworkPath in embeddedFrameworkPaths) {
        val result = manipulator.addFile(
            embeddedFrameworkPath,
            targetMemberships,
            frameworksGroup,
            false,
            null,
            PBXBuildPhase.Type.FRAMEWORKS,
            false
        )
        val buildFile = manipulator.addToBuildPhase(pbxTarget, embedFrameworksPhase, result.reference)
        buildFile.setAttribute("settings", PBXDictionary(pbxProjectFile).apply {
            setAttribute("ATTRIBUTES", listOf("CodeSignOnCopy", "RemoveHeadersOnCopy"))
        })
    }

    //TODO: seems to work, but feels hacky! Is there a better way to convince Xcode that we'll provide the correct framework?
    manipulator.addBuildPhase(
        PBXBuildPhase.Type.SHELL_SCRIPT, mapOf(
            "name" to "Cleanup Staged Frameworks",
            "shellPath" to "/bin/sh",
            "shellScript" to "#!/bin/sh\nrm -rf \"\$SRCROOT/${frameworksStagingPathString}\"\n",
            "alwaysOutOfDate" to "1"
        ), pbxTarget
    )
}

private fun writePlist(
    baseDir: File,
    name: String,
    map: Plist,
): File {
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
    XMLPlistDriver().write(plist, file)
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