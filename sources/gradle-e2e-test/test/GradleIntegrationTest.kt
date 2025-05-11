/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.TempDirExtension
import org.jetbrains.amper.test.server.withFileServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Ignore
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class GradleIntegrationTest : GradleE2ETestFixture("./testData/projects/") {

    @RegisterExtension
    private val tempDirExtension = TempDirExtension()

    private val tempRoot: Path
        get() = tempDirExtension.path

    @Test
    fun `running jvm - Gradle 8_7`() = test(
        projectName = "jvm-basic",
        "run",
        expectOutputToHave = "Hello, World!",
        gradleVersion = "8.7",
    )

    @Test
    fun `running jvm - Gradle 8_11`() = test(
        projectName = "jvm-basic",
        "run",
        expectOutputToHave = "Hello, World!",
        gradleVersion = "8.11.1",
    )

    @Test
    fun `running jvm using runJvm`() = test(
        projectName = "jvm-basic",
        "runJvm",
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
        val m2groupRoot = Dirs.m2repository.resolve("com/mytestgroup")
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
    fun `compose desktop with Gradle 8_7`() = test(
        projectName = "compose-desktop",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
        gradleVersion = "8.7",
    )

    @Test
    fun `compose desktop with Gradle 8_9`() = test(
        projectName = "compose-desktop",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
        gradleVersion = "8.9",
    )

    @Test
    fun `compose desktop with Gradle 8_11`() = test(
        projectName = "compose-desktop",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
        gradleVersion = "8.11.1",
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
    fun `configure a project with most of the settings - gradle 8_7`() = test(
        projectName = "settings",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
        gradleVersion = "8.7",
    )

    @Test
    @KonanFolderLock
    fun `configure a project with most of the settings - gradle 8_11`() = test(
        projectName = "settings",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
        gradleVersion = "8.11.1",
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
    fun `testing gradle interoperability with gradle-jvm layout - gradle 8_7`() = test(
        projectName = "gradle-interoperability-gradle-jvm-layout",
        "run", "test",
        expectOutputToHave = listOf("Hello, World!", "The test is running"),
        gradleVersion = "8.7",
    )

    @Test
    fun `testing gradle interoperability with gradle-jvm layout - gradle 8_9`() = test(
        projectName = "gradle-interoperability-gradle-jvm-layout",
        "run", "test",
        expectOutputToHave = listOf("Hello, World!", "The test is running"),
        gradleVersion = "8.9",
    )

    @Test
    fun `testing gradle interoperability with gradle-jvm layout - gradle 8_11`() = test(
        projectName = "gradle-interoperability-gradle-jvm-layout",
        "run", "test",
        expectOutputToHave = listOf("Hello, World!", "The test is running"),
        gradleVersion = "8.11.1",
    )

    @Test
    fun `testing gradle interoperability with amper layout`() = test(
        projectName = "gradle-interoperability-amper-layout",
        "testRun",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `respect dependencyResolutionManagement block in non-amper subprojects`() = runTest(timeout = 3.minutes) {
        val fakeMavenRoot = tempRoot.resolve("fake-maven-root").also { it.createDirectories() }

        val fakeDepDir = fakeMavenRoot.resolve("com/example/unique/my-unique-dep/1.0.0").createDirectories()
        fakeDepDir.resolve("my-unique-dep-1.0.0.pom").writeText("""
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example.unique</groupId>
              <artifactId>my-unique-dep</artifactId>
              <version>1.0.0</version>
            </project>
        """.trimIndent())
        val testJar = Path("${pathToProjects}/gradle-interoperability-settings-deps-management/my-unique-dep-1.0.0.jar")
        testJar.copyTo(fakeDepDir.resolve("my-unique-dep-1.0.0.jar"))

        withFileServer(wwwRoot = fakeMavenRoot, testReporter = testReporter) { baseUrl ->
            test(
                projectName = "gradle-interoperability-settings-deps-management",
                "assemble",
                expectOutputToHave = "BUILD SUCCESSFUL",
                additionalEnv = mapOf("FAKE_MAVEN_REPO_URL" to baseUrl)
            )
        }
    }

    @Test
    fun `compose-desktop packaging`() = test(
        projectName = "compose-desktop-packaging",
        "packageDistributionForCurrentOS",
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
    fun `compose default version should fail`() = test(
        projectName = "compose-default-version",
        "assemble",
        expectOutputToHave = "Gradle-based Amper does not support Compose version ${UsedVersions.composeVersion} " +
                "(which is the new default). The only supported version is 1.6.10. Please set the Compose version to " +
                "1.6.10 explicitly in your module.yaml settings, or try the standalone version of Amper.",
        shouldSucceed = false,
    )

    @Test
    fun `compose non-1_6_10 version should fail`() = test(
        projectName = "compose-unsupported-version",
        "assemble",
        expectOutputToHave = "Gradle-based Amper does not support Compose version 1.7.0. The only supported version " +
                "is 1.6.10. Please set the Compose version to 1.6.10 explicitly in your module.yaml settings, or try " +
                "the standalone version of Amper.",
        shouldSucceed = false,
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
