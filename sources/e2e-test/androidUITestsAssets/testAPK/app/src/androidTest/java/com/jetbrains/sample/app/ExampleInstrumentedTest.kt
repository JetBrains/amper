package com.jetbrains.sample.app



/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
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
        val launcherPackage = device.launcherPackageName
        assertThat(launcherPackage, notNullValue())
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), launchTimeout)

        // Launch the app
        device.executeShellCommand("am start -n $sampleAppPackage/.MainActivity")
        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(sampleAppPackage).depth(0)), launchTimeout)
    }

    @Test
    fun checkAppOpens() {
        // Check that the app has opened by verifying that a view in the app is displayed
        assertThat(device.findObject(By.pkg(sampleAppPackage)), notNullValue())
    }
}
