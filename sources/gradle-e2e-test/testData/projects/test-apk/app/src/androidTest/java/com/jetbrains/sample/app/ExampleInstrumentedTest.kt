package com.jetbrains.sample.app

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppOpenTest {

    private val sampleAppPackage = "com.jetbrains.sample.app"
    private val launchTimeout = 5000L
    private lateinit var device: UiDevice

    @Before
    fun startMainActivityFromHomeScreen() {
        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Start from the home screen
        device.pressHome()

        // Wait for launcher
        val launcherPackage = "com.android.launcher3" // device.launcherPackageName is unreliable
        assertThat(launcherPackage, notNullValue())
        val launcherAppeared = device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), launchTimeout)
        assertThat("Launcher '$launcherPackage' should be present but isn't", launcherAppeared, `is`(true))

        // Launch the app using adb monkey and sampleAppPackage
        // Note:
        // 1) "am start -n $sampleAppPackage/.MainActivity" doesn't work for external projects, even with the
        // overridden sampleAppPackage value, because it needs the FQN of the activity. Better simulate a click.
        // 2) the monkey command needs "--pct-syskeys 0" otherwise it fails on macOS with missing physical keys
        val appStartCommand = "monkey -p $sampleAppPackage -c android.intent.category.LAUNCHER --pct-syskeys 0 1"
        val appStartCommandOutput = device.executeShellCommand(appStartCommand)

        // Wait for the app to appear
        val appAppeared = device.wait(Until.hasObject(By.pkg(sampleAppPackage).depth(0)), launchTimeout)
        assertThat("App should be present but isn't", appAppeared, `is`(true))
    }

    @Test
    fun checkAppOpens() {
        // Check that the app has opened by verifying that a view in the app is displayed
        assertThat(device.findObject(By.pkg(sampleAppPackage)), notNullValue())
    }
}
