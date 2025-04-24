/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.tools

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.commands.AmperSubcommand
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.cli.withBackend
import com.github.ajalt.clikt.core.terminal
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.processes.runProcessWithInheritedIO
import org.jetbrains.amper.tasks.ios.IosConventions
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.writeText

internal class XCodeIntegrationCommand : AmperSubcommand(name = "xcode-integration") {

    private val env: Map<String, String> = System.getenv()

    override val hiddenFromHelp: Boolean
        get() = true

    override suspend fun run() {
        validateGeneralXcodeEnvironment()

        val superAmperBuildRoot = env[AMPER_BUILD_OUTPUT_DIR_ENV]
        val buildType = inferBuildTypeFromEnv().let { inferred ->
            if (inferred != BuildType.Debug) {
                // TODO: Support Release configuration in Amper
                logger.warn(
                    "Amper doesn't yet support building Kotlin for `${inferred.name}` configuration. " +
                            "Falling back to `Debug`"
                )
            }
            BuildType.Debug
        }
        val platform = inferPlatformFromEnv()

        val (buildDir: Path, moduleName: String) = if (superAmperBuildRoot == null) {
            // Running from xcode only - need to run iOS prebuild task ourselves
            withBackend(commonOptions, commandName, terminal) { backend ->
                val moduleName = backend.prebuildForXcode(
                    moduleDir = Path(requireXcodeVar("PROJECT_DIR")),
                    buildType = buildType,
                    platform = platform,
                )
                backend.context.buildOutputRoot.path to moduleName
            }
        } else {
            // Running from the super Amper call - everything is already built.
            // NOTE: We do not make `withBackend` call here as it may interfere with the super call.
            Path(superAmperBuildRoot) to checkNotNull(env[AMPER_MODULE_NAME_ENV])
        }

        val iosConventions = IosConventions(
            buildRootPath = buildDir,
            moduleName = moduleName,
            buildType = buildType,
            platform = platform,
        )

        val xcodeTargetBuildDir = Path(requireXcodeVar("TARGET_BUILD_DIR"))

        embedAndSignFramework(iosConventions, xcodeTargetBuildDir)

        embedComposeResources(iosConventions, xcodeTargetBuildDir)

        writeOutputDescription(iosConventions)
    }

    private fun validateGeneralXcodeEnvironment() {
        if (env["ENABLE_USER_SCRIPT_SANDBOXING"] == "YES") {
            userReadableError(
                "XCode option 'ENABLE_USER_SCRIPT_SANDBOXING' is enabled, which is unsupported. " +
                        "Please disable `User Script Sandboxing` option explicitly in XCode."
            )
        }
    }

    private fun writeOutputDescription(iosConventions: IosConventions) {
        val outputDescription = IosConventions.BuildOutputDescription(
            appPath = getBuiltAppDirectory().also { appPath ->
                check(appPath.isDirectory()) {
                    "Expected app path '$appPath' to be an existing directory"
                }
            }.absolutePathString(),
            productBundleId = requireXcodeVar("PRODUCT_BUNDLE_IDENTIFIER"),
        )
        iosConventions.getBuildOutputDescriptionFilePath().writeText(Json.encodeToString(outputDescription))
    }

    private suspend fun embedComposeResources(
        iosConventions: IosConventions,
        xcodeTargetBuildDir: Path,
    ) {
        val embeddedComposeResourcesDir = xcodeTargetBuildDir / requireXcodeVar("CONTENTS_FOLDER_PATH") /
                IosConventions.COMPOSE_RESOURCES_CONTENT_DIR_NAME
        if (iosConventions.getComposeResourcesDirectory().isDirectory()) {
            embeddedComposeResourcesDir.apply {
                createParentDirectories()
                deleteRecursively()
            }
            BuildPrimitives.copy(
                from = iosConventions.getComposeResourcesDirectory(),
                to = embeddedComposeResourcesDir,
                followLinks = true,
            )
        }
    }

    private suspend fun embedAndSignFramework(
        iosConventions: IosConventions,
        xcodeTargetBuildDir: Path,
    ) {
        val embeddedFrameworkPath =
            xcodeTargetBuildDir / requireXcodeVar("FRAMEWORKS_FOLDER_PATH") / IosConventions.KOTLIN_FRAMEWORK_NAME
        embeddedFrameworkPath.createParentDirectories()
        BuildPrimitives.copy(
            from = iosConventions.getAppFrameworkPath(),
            to = embeddedFrameworkPath,
            followLinks = true,
            overwrite = true,
        )

        env["EXPANDED_CODE_SIGN_IDENTITY"]?.let { envSign ->
            val binary = embeddedFrameworkPath / embeddedFrameworkPath.nameWithoutExtension
            runProcessWithInheritedIO(
                workingDir = embeddedFrameworkPath,
                command = listOf("codesign", "--force", "--sign", envSign, "--", binary.pathString),
            )
        }
    }

    private fun getBuiltAppDirectory(): Path = Path(requireXcodeVar("SYMROOT")) /
            "${requireXcodeVar("CONFIGURATION")}-${requireXcodeVar("PLATFORM_NAME")}" /
            "${requireXcodeVar("PRODUCT_NAME")}.app"

    private fun inferBuildTypeFromEnv(): BuildType = requireXcodeVar("CONFIGURATION").let { value ->
        BuildType.entries.find { it.name == value } ?: userReadableError("Invalid `CONFIGURATION`: `$value`")
    }

    private fun inferPlatformFromEnv(): Platform {
        val sdk: String = requireXcodeVar("PLATFORM_NAME")
        return requireXcodeVar("ARCHS").split(' ').let { archs ->
            archs.singleOrNull()
                ?: userReadableError("Building multiple architectures in a single call is unsupported for now: $archs")
        }.let { value ->
            when ("$value:$sdk") {
                "arm64:$IPHONE_SDK_NAME" -> Platform.IOS_ARM64
                "arm64:$SIMULATOR_SDK_NAME" -> Platform.IOS_SIMULATOR_ARM64
                "x86_64:$SIMULATOR_SDK_NAME" -> Platform.IOS_X64
                else -> userReadableError("Amper currently doesn't support building the following configuration: " +
                        "arch=$value, sdk=$sdk")
            }
        }
    }

    private fun requireXcodeVar(name: String): String {
        return env[name] ?: userReadableError(
            "Invalid environment: missing xcode variable `$name`"
        )
    }

    companion object {
        /**
         * If set, signals the integration command that there is a super Amper call and all the necessary tasks have
         * been run.
         */
        const val AMPER_BUILD_OUTPUT_DIR_ENV = "AMPER_XCI_BUILD_OUTPUT_DIR"

        /**
         * iOS module name that super Amper call currently builds.
         * Has the same semantics as [AMPER_BUILD_OUTPUT_DIR_ENV].
         */
        const val AMPER_MODULE_NAME_ENV = "AMPER_XCI_MODULE_NAME"

        private const val SIMULATOR_SDK_NAME = "iphonesimulator"
        private const val IPHONE_SDK_NAME = "iphoneos"
    }
}
