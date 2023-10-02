import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.test.assertTrue

class IntegrationTest : E2ETestFixture("./testData/projects/") {
    @Test
    fun `running jvm`() = test(
        projectName = "jvm-basic",
        "run",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `configuring native macos`() = test(
        projectName = "native-macos",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
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
    @EnabledOnOs(value = [OS.MAC], architectures = ["aarch64"])
    fun `detecting native entry point`() = test(
        projectName = "entry-point-detection-native",
        "runMacosArm64DebugExecutableMacosArm64",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    @EnabledOnOs(value = [OS.LINUX], architectures = ["amd64"])
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
    fun `compose desktop`() = test(
        projectName = "compose-desktop",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
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
    fun templates() = test(
        "templates",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `build-variants`() = test(
        "build-variants",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    @EnabledOnOs(value = [OS.MAC])
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
    fun `testing gradle interoperability with deft layout`() = test(
        projectName = "gradle-interoperability-deft-layout",
        "testRun",
        expectOutputToHave = "Hello, World!",
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
        val pathToMergedResources = it / "build" / "intermediates" / "java_res" / "release" / "out"
        assertTrue(
            pathToMergedResources.resolve("commonResource.txt").exists(),
            "Expected to have common resource in merged resources"
        )
        assertTrue(
            pathToMergedResources.resolve("androidResource.txt").exists(),
            "Expected to have android resource in merged resources"
        )
    }
}