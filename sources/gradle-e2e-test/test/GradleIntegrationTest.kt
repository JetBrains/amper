/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.test.Ignore
import kotlin.test.assertTrue

class GradleIntegrationTest : GradleE2ETestFixture("./testData/projects/") {
    @Test
    fun `running jvm`() = test(
        projectName = "jvm-basic",
        "run",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `running jvm using jvmRun`() = test(
        projectName = "jvm-basic",
        "jvmRun",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `jvm-free-compiler-args JVM compilation should fail`() = test(
        projectName = "jvm-free-compiler-args",
        "assemble",
        expectOutputToHave = "Visibility must be specified in explicit API mode",
        shouldSucceed = false,
    )

    @Test
    fun `jvm-release-8`() = test(
        projectName = "jvm-release-8",
        "run",
        expectOutputToHave = "I'm JDK 8 compliant",
    )

    @Test
    fun `jvm-release-8-using-9-apis`() = test(
        projectName = "jvm-release-8-using-9-apis",
        "assemble",
        expectOutputToHave = "Unresolved reference 'of'", // for List.of()
        shouldSucceed = false,
    )

    @Test
    fun `jvm-release-11-using-17-apis`() = test(
        projectName = "jvm-release-11-using-17-apis",
        "assemble",
        expectOutputToHave = "Unresolved reference 'InstantSource'",
        shouldSucceed = false,
    )

    @Test
    @KonanFolderLock
    fun `configuring native macos`() = test(
        projectName = "native-macos",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    @KonanFolderLock
    fun `configuring native linux`() = test(
        projectName = "native-linux",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `configuring jvm dependencies`() = test(
        projectName = "jvm-dependencies",
        "test",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `detecting jvm entry point`() = test(
        projectName = "entry-point-detection-jvm",
        "run",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    @KonanFolderLock
    @EnabledOnOs(value = [OS.MAC], architectures = ["aarch64"])
    fun `detecting native entry point on mac`() = test(
        projectName = "entry-point-detection-native",
        "runMacosArm64DebugExecutableMacosArm64",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    @EnabledOnOs(value = [OS.LINUX], architectures = ["amd64"])
    @KonanFolderLock
    fun `detecting native entry point on linux (CI)`() = test(
        projectName = "entry-point-detection-native-linux",
        "runLinuxX64DebugExecutableLinuxX64",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `implicit kotlin tests`() = test(
        projectName = "implicit-kotlin-tests",
        "test",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `disabling junit platform`() = test(
        projectName = "disable-junit",
        ":cleanJvmTest", ":jvmTest", "--tests", "SimpleTest.test",
        shouldSucceed = false,
        expectOutputToHave = "> No tests found for given includes: [SimpleTest.test](--tests filter)",
    )

    @Test
    fun `if we have only one platform, platform sourceSet becomes common, so we need to apply settings to common and platform-specific sourceSet simultaneously`() =
        test(
            projectName = "language-version",
            "assemble",
            expectOutputToHave = "BUILD SUCCESSFUL",
        )

    @Test
    fun `jvm+android assembles`() = test(
        projectName = "jvm+android",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `publish to maven local with custom artifact id`() {
        val m2groupRoot = TestUtil.m2repository.resolve("com/mytestgroup")
        m2groupRoot.deleteRecursively()
        GradleDaemonManager.deleteFileOrDirectoryOnExit(m2groupRoot)

        test(
            projectName = "publish-custom-artifactid",
            "publishToMavenLocal",
            expectOutputToHave = "BUILD SUCCESSFUL",
            additionalCheck = {
                for (file in listOf(m2groupRoot.resolve("myname/111/myname-111.jar"), m2groupRoot.resolve("myname/111/myname-111.pom"))) {
                    check(file.exists()) {
                        "Does not exist: $file"
                    }
                }
            }
        )
    }

    @Test
    fun `compose desktop`() = test(
        projectName = "compose-desktop",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `compose desktop with Gradle 8_7`() = test(
        projectName = "compose-desktop",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
        gradleVersion = "8.7",
    )

    @Test
    fun `compose android`() = test(
        projectName = "compose-android",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `language-version 1_9`() = test(
        projectName = "language-version-1-9",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    @KonanFolderLock
    fun `2 targets, language version 1-9 for common code should not fail`() = test(
        "multiplatform-lib-propagation",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `android app, language version 1-9 for common code should not fail`() = test(
        "android-language-version-1-9",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    @KonanFolderLock
    fun `configure a project with most of the settings`() = test(
        "settings",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun multiplatform() = test(
        "multiplatform",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `multiplatform compilation should fail with common explicit-api arg`() {
        test(
            projectName = "multiplatform-free-compiler-args",
            ":common-args:compileCommonMainKotlinMetadata",
            shouldSucceed = false,
            additionalCheck = {
                assertTaskFailed(":common-args:compileCommonMainKotlinMetadata")
                assertOutputContains("common-args/src/LibCommon.kt:2:1 Visibility must be specified in explicit API mode")
            }
        )
        test(
            projectName = "multiplatform-free-compiler-args",
            ":common-args:compileReleaseKotlinAndroid",
            shouldSucceed = false,
            additionalCheck = {
                assertTaskFailed(":common-args:compileReleaseKotlinAndroid")
                assertOutputContains("common-args/src/LibCommon.kt:2:1 Visibility must be specified in explicit API mode")
                assertOutputContains("common-args/src@android/LibAndroid.kt:2:1 Visibility must be specified in explicit API mode")
            }
        )
        test(
            projectName = "multiplatform-free-compiler-args",
            ":common-args:compileDebugKotlinAndroid",
            shouldSucceed = false,
            additionalCheck = {
                assertTaskFailed(":common-args:compileDebugKotlinAndroid")
                assertOutputContains("common-args/src/LibCommon.kt:2:1 Visibility must be specified in explicit API mode")
                assertOutputContains("common-args/src@android/LibAndroid.kt:2:1 Visibility must be specified in explicit API mode")
            }
        )
        test(
            projectName = "multiplatform-free-compiler-args",
            ":common-args:compileKotlinJs",
            ":common-args:compileKotlinJvm",
            shouldSucceed = false,
            additionalCheck = {
                assertTaskFailed(":common-args:compileKotlinJs")
                assertOutputContains("common-args/src/LibCommon.kt:2:1 Visibility must be specified in explicit API mode")
                assertOutputContains("common-args/src@js/LibJs.kt:2:1 Visibility must be specified in explicit API mode")
            }
        )
        test(
            projectName = "multiplatform-free-compiler-args",
            ":common-args:compileKotlinJvm",
            shouldSucceed = false,
            additionalCheck = {
                assertTaskFailed(":common-args:compileKotlinJvm")
                assertOutputContains("common-args/src/LibCommon.kt:2:1 Visibility must be specified in explicit API mode")
                assertOutputContains("common-args/src@jvm/LibJvm.kt:2:1 Visibility must be specified in explicit API mode")
            }
        )
    }

    @Test
    fun `multiplatform compilation should partially fail with platform-specific explicit-api arg`() {
        test(
            projectName = "multiplatform-free-compiler-args",
            ":platform-args:compileCommonMainKotlinMetadata",
            shouldSucceed = false,
            additionalCheck = {
                // explicit API mode is only in settings@jvm, so it shouldn't fail common metadata compilations
                assertTaskSucceeded(":platform-args:compileCommonMainKotlinMetadata")
                // common code should only fail as part of the JVM compilation
                assertNotInOutput("platform-args/src/LibCommon.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@jvm/LibJvm.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@android/LibAndroid.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@js/LibJs.kt:2:1 Visibility must be specified in explicit API mode")
            }
        )
        test(
            projectName = "multiplatform-free-compiler-args",
            ":platform-args:compileReleaseKotlinAndroid",
            shouldSucceed = false,
            additionalCheck = {
                // explicit API mode is only in settings@jvm, so it shouldn't fail Android compilations
                assertTaskSucceeded(":platform-args:compileReleaseKotlinAndroid")
                // common code should only fail as part of the JVM compilation
                assertNotInOutput("platform-args/src/LibCommon.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@jvm/LibJvm.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@android/LibAndroid.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@js/LibJs.kt:2:1 Visibility must be specified in explicit API mode")
            }
        )
        test(
            projectName = "multiplatform-free-compiler-args",
            ":platform-args:compileDebugKotlinAndroid",
            shouldSucceed = false,
            additionalCheck = {
                // explicit API mode is only in settings@jvm, so it shouldn't fail Android compilations
                assertTaskSucceeded(":platform-args:compileDebugKotlinAndroid")
                // common code should only fail as part of the JVM compilation
                assertNotInOutput("platform-args/src/LibCommon.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@jvm/LibJvm.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@android/LibAndroid.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@js/LibJs.kt:2:1 Visibility must be specified in explicit API mode")
            }
        )
        test(
            projectName = "multiplatform-free-compiler-args",
            ":platform-args:compileKotlinJs",
            shouldSucceed = false,
            additionalCheck = {
                // explicit API mode is only in settings@jvm, so it shouldn't fail JS compilation
                assertTaskSucceeded(":platform-args:compileKotlinJs")
                // common code should only fail as part of the JVM compilation
                assertNotInOutput("platform-args/src/LibCommon.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@jvm/LibJvm.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@android/LibAndroid.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@js/LibJs.kt:2:1 Visibility must be specified in explicit API mode")
            }
        )
        test(
            projectName = "multiplatform-free-compiler-args",
            ":platform-args:compileKotlinJvm",
            shouldSucceed = false,
            additionalCheck = {
                // explicit API mode is in settings@jvm, so it should fail the JVM compilation
                assertTaskFailed(":platform-args:compileKotlinJvm")
                // common code should fail as part of the JVM compilation
                assertOutputContains("platform-args/src/LibCommon.kt:2:1 Visibility must be specified in explicit API mode")
                assertOutputContains("platform-args/src@jvm/LibJvm.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@android/LibAndroid.kt:2:1 Visibility must be specified in explicit API mode")
                assertNotInOutput("platform-args/src@js/LibJs.kt:2:1 Visibility must be specified in explicit API mode")
            }
        )
    }

    @Test
    fun templates() = test(
        "templates",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    @Ignore("Ignore variants for now")
    fun `build-variants`() = test(
        "build-variants",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
        // TODO Variants produce warning aboud kotlin source sets trees. Need to fix when we will
        // stabilize variants.
        checkForWarnings = false,
    )

    @Test
    @EnabledOnOs(value = [OS.MAC])
    @KonanFolderLock
    fun iosApp() = test(
        "ios-app",
        "buildIosAppMain",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `testing gradle interoperability with gradle layout`() = test(
        projectName = "gradle-interoperability-gradle-layout",
        "testRun",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `testing gradle interoperability with gradle-jvm layout`() = test(
        projectName = "gradle-interoperability-gradle-jvm-layout",
        "run",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `testing gradle interoperability with amper layout`() = test(
        projectName = "gradle-interoperability-amper-layout",
        "testRun",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `respect dependencyResolutionManagement block in non-amper subprojects`() = test(
        projectName = "gradle-interoperability-settings-deps-management",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `compose-desktop packaging`() = test(
        projectName = "compose-desktop-packaging",
        "package",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `testing android common resources propagation`() = test(
        projectName = "android-common-resources-propagation",
        "mergeReleaseJavaResource",
        expectOutputToHave = "BUILD SUCCESSFUL",
    ) {
        val pathToMergedResources = projectDir / "build" / "intermediates" / "java_res" / "release" / "out"
        assertTrue(
            pathToMergedResources.resolve("commonResource.txt").exists(),
            "Expected to have common resource in merged resources"
        )
        assertTrue(
            pathToMergedResources.resolve("androidResource.txt").exists(),
            "Expected to have android resource in merged resources"
        )
    }

    @Test
    fun `kotlin serialization`() = test(
        projectName = "kotlin-serialization",
        "run",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `kotlin serialization with default lib`() = test(
        projectName = "kotlin-serialization-default",
        "run",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    @EnabledOnOs(value = [OS.MAC])
    @KonanFolderLock
    fun multiplatformIosFramework() {
        test(
            projectName = "multiplatform-lib-ios-framework",
            ":shared:embedAndSignAppleFrameworkForXcode",
            expectOutputToHave = "BUILD SUCCESSFUL",
            additionalEnv = mapOf(
                "CONFIGURATION" to "Debug",
                "SDK_NAME" to "iphoneos",
                "ARCHS" to "arm64",
                "EXPANDED_CODE_SIGN_IDENTITY" to "-",
                "TARGET_BUILD_DIR" to "./target_xcode_build",
                "FRAMEWORKS_FOLDER_PATH" to "testFrameworksDir",
                "BUILT_PRODUCTS_DIR" to "testBuiltProductsDir",
            )
        )
    }

    @Test
    fun `check adding repositories`() = test(
        projectName = "adding-repositories",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    @KonanFolderLock
    fun `library with all platforms`() = test(
        projectName = "lib-with-all-platforms",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `compose dev version change`() = test(
        projectName = "compose-dev-version-change",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `compose dev version change with Gradle 8_7 should fail`() = test(
        projectName = "compose-dev-version-change",
        "assemble",
        expectOutputToHave = "Amper does not support custom Compose versions with Gradle > 8.6 (current is 8.7)",
        shouldSucceed = false,
        gradleVersion = "8.7",
    )

    @Test
    fun `compose version conflict`() = test(
        projectName = "compose-version-conflict",
        "assemble",
        shouldSucceed = false,
        expectOutputToHave = "Currently, Compose versions should be the same across all module files"
    )

    @Test
    fun `Gradle BOM support`() = test(
        projectName = "gradle-interoperability-bom",
        "run",
        expectOutputToHave = "[main] INFO MyClass -- Hello, world!"
    )

    @Test
    @EnabledOnOs(value = [OS.MAC])
    @KonanFolderLock
    fun `overall platforms test`() = test(
        projectName = "overall-platforms-test",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `test compose resources jvm`() = test(
        projectName = "compose-resources-jvm",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `test compose resources jvm android`() = test(
        projectName = "compose-resources-jvm-android",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )
}
