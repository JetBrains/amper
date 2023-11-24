import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

class MigratedProjectsTest : E2ETestFixture("../../migrated-projects/") {
    @Test
    fun `compose-multiplatform-ios-android-template (android)`() = test(
        projectName = "compose-multiplatform-ios-android-template",
        ":androidApp:assembleDebug",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    @EnabledOnOs(value = [OS.MAC])
    fun `compose-multiplatform-ios-android-template (ios)`() = test(
        projectName = "compose-multiplatform-ios-android-template",
        ":shared:linkPodDebugFrameworkIosSimulatorArm64",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `compose-multiplatform-desktop-template`() = test(
        projectName = "compose-multiplatform-desktop-template",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `compose-multiplatform-template`() = test(
        projectName = "compose-multiplatform-template",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    @EnabledOnOs(value = [OS.MAC])
    fun `compose-multiplatform-template (ios)`() = test(
        projectName = "compose-multiplatform-template",
        ":shared:linkDebugFrameworkIosSimulatorArm64",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )
}