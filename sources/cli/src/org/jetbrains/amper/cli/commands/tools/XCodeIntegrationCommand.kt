/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.tools

import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.commands.AmperSubcommand
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.ios.IosConventions
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText

internal class XCodeIntegrationCommand : AmperSubcommand(name = "xcode-integration") {
    private val env: Map<String, String> = System.getenv()

    private val module by option("--module")
        .required()

    override val hiddenFromHelp: Boolean
        get() = true

    override suspend fun run() {
        val superAmperBuildBuildRoot = env[AMPER_BUILD_OUTPUT_DIR_ENV]

        // TODO: Additionally validate xcode environment and give actionable error messages about configuration.

        val iosContext = if (superAmperBuildBuildRoot == null) {
            // Running from xcode only - need to run iOS prebuild task ourselves
            withBackend(commonOptions, commandName) { backend ->
                val iosContext = inferXcodeContextFromEnv(
                    buildOutputRootPath = backend.context.buildOutputRoot.path,
                )
                backend.prebuildForXcode(
                    moduleName = module,
                    platform = iosContext.platform,
                    buildType = iosContext.buildType,
                )
                iosContext
            }
        } else {
            // Running from the super Amper call - everything is already built.
            // NOTE: We do not make `withBackend` call here as it may interfere with the super call.
            inferXcodeContextFromEnv(
                buildOutputRootPath = Path(superAmperBuildBuildRoot),
            )
        }

        // Copy the xcodebuild dependencies to their respective conventional locations.
        with(iosContext) {
            val xcodeTargetBuildDir = Path(getXcodeVar("TARGET_BUILD_DIR"))

            val embeddedFrameworkPath =
                xcodeTargetBuildDir / getXcodeVar("FRAMEWORKS_FOLDER_PATH")
            embeddedFrameworkPath.createDirectories()
            BuildPrimitives.copy(
                from = IosConventions.getAppFrameworkPath(),
                to = embeddedFrameworkPath / IosConventions.KOTLIN_FRAMEWORK_NAME,
                followLinks = true,
                overwrite = true,
            )

            val embeddedComposeResourcesDir = xcodeTargetBuildDir / getXcodeVar("CONTENTS_FOLDER_PATH") /
                    IosConventions.COMPOSE_RESOURCES_CONTENT_DIR_NAME
            if (IosConventions.getComposeResourcesDirectory().isDirectory()) {
                embeddedComposeResourcesDir.apply {
                    createParentDirectories()
                    deleteRecursively()
                }
                BuildPrimitives.copy(
                    from = IosConventions.getComposeResourcesDirectory(),
                    to = embeddedComposeResourcesDir,
                    followLinks = true,
                )
            }

            val outputDescription = IosConventions.BuildOutputDescription(
                appPath = getBuiltAppDirectory().also { appPath ->
                    check(appPath.isDirectory()) {
                        "Expected app path '$appPath' to be an existing directory"
                    }
                }.absolutePathString(),
                productBundleId = getXcodeVar("PRODUCT_BUNDLE_IDENTIFIER"),
            )
            IosConventions.getBuildOutputDescriptionFilePath().writeText(Json.encodeToString(outputDescription))
        }
    }

    private fun getBuiltAppDirectory(): Path = Path(getXcodeVar("SYMROOT")) /
            "${getXcodeVar("CONFIGURATION")}-${getXcodeVar("PLATFORM_NAME")}" /
            "${getXcodeVar("PRODUCT_NAME")}.app"

    private fun inferXcodeContextFromEnv(
        buildOutputRootPath: Path,
    ): IosConventions.Context {
        val buildType: BuildType = getXcodeVar("CONFIGURATION").let { value ->
            BuildType.entries.find { it.name == value } ?: userReadableError("Invalid `CONFIGURATION`: `$value`")
        }
        val sdk: String = getXcodeVar("PLATFORM_NAME")
        val platform: Platform = getXcodeVar("ARCHS").split(' ').let { archs ->
            archs.singleOrNull()
                ?: userReadableError("Building multiple architectures in a single call is unsupported for now: $archs")
        }.let { value ->
            fun unsupported(): Nothing = userReadableError("Unsupported platform (sdk=`$sdk`, arch=`$value`)")

            // TODO: Write this fully and properly
            when (value) {
                "arm64" -> when (sdk) {
                    "iphoneos" -> Platform.IOS_ARM64
                    "iphonesimulator" -> Platform.IOS_SIMULATOR_ARM64
                    else -> unsupported()
                }

                "x86_64" -> when (sdk) {
                    "iphoneos" -> Platform.IOS_X64  // TODO: Is this correct?
                    "iphonesimulator" -> Platform.IOS_X64
                    else -> unsupported()
                }

                else -> unsupported()
            }
        }

        return IosConventions.Context(
            buildRootPath = buildOutputRootPath,
            platform = platform,
            buildType = buildType,
            moduleName = module,
        )
    }

    private fun getXcodeVar(name: String): String {
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
    }
}