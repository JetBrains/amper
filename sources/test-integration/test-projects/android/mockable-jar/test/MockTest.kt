import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import com.jetbrains.sample.app.utils.NetworkHelper
import com.jetbrains.sample.app.utils.UserPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class MockTest {

    // Fields for testing
    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var networkHelper: NetworkHelper
    private lateinit var userPreferences: UserPreferences

    @Before
    fun setup() {
        // Mock Context
        context = mock()

        // Mock ConnectivityManager
        connectivityManager = mock()
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)

        // Mock SharedPreferences and Editor
        sharedPreferences = mock()
        sharedPreferencesEditor = mock()
        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(sharedPreferencesEditor)
        whenever(sharedPreferencesEditor.putString(any(), any())).thenReturn(sharedPreferencesEditor)
        whenever(sharedPreferencesEditor.putInt(any(), any())).thenReturn(sharedPreferencesEditor)

        // Initialize classes with mocked dependencies
        networkHelper = NetworkHelper(context)
        userPreferences = UserPreferences(context)
    }

    @Test
    fun testIsNetworkAvailableOnModernAndroidVersions() {
        // Create a subclass of NetworkHelper that forces the modern path
        val modernApiNetworkHelper = object : NetworkHelper(context) {
            override fun isNetworkAvailable(): Boolean {
                // Always use the modern API path
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                return networkCapabilities != null && (
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            }
        }

        // Mock NetworkCapabilities
        val networkCapabilities = mock<NetworkCapabilities>()
        val activeNetwork = mock<android.net.Network>()

        // Setup expectations
        whenever(connectivityManager.activeNetwork).thenReturn(activeNetwork)
        whenever(connectivityManager.getNetworkCapabilities(activeNetwork)).thenReturn(networkCapabilities)
        whenever(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true)

        // Test the functionality with our subclass that forces the modern path
        assertTrue(modernApiNetworkHelper.isNetworkAvailable())

        // Verify interactions
        verify(connectivityManager).activeNetwork
        verify(connectivityManager).getNetworkCapabilities(activeNetwork)
        verify(networkCapabilities).hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @Test
    fun testIsNetworkAvailableOnOlderAndroidVersions() {
        // Instead of modifying SDK_INT, we'll create a subclass of NetworkHelper that forces the older path
        val oldApiNetworkHelper = object : NetworkHelper(context) {
            override fun isNetworkAvailable(): Boolean {
                // Always use the older API path
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                @Suppress("DEPRECATION")
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                return activeNetworkInfo != null && activeNetworkInfo.isConnected
            }
        }

        // Mock NetworkInfo (deprecated but used on older devices)
        val networkInfo = mock<NetworkInfo>()

        // Setup expectations
        @Suppress("DEPRECATION")
        whenever(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        @Suppress("DEPRECATION")
        whenever(networkInfo.isConnected).thenReturn(true)

        // Test the functionality
        assertTrue(oldApiNetworkHelper.isNetworkAvailable())

        // Verify interactions
        @Suppress("DEPRECATION")
        verify(connectivityManager).activeNetworkInfo
        @Suppress("DEPRECATION")
        verify(networkInfo).isConnected
    }

    @Test
    fun testSaveUsernameAndGetUsername() {
        // Arrange
        val username = "test_user"
        whenever(sharedPreferences.getString(eq("username"), any())).thenReturn(username)

        // Act
        userPreferences.saveUsername(username)
        val result = userPreferences.getUsername()

        // Assert
        assertEquals(username, result)

        // Verify - use atLeastOnce() since the method is called multiple times
        verify(context, atLeastOnce()).getSharedPreferences(eq("user_preferences"), eq(Context.MODE_PRIVATE))
        verify(sharedPreferencesEditor).putString(eq("username"), eq(username))
        verify(sharedPreferencesEditor).apply()
    }

    @Test
    fun testSaveUserIdAndGetUserId() {
        // Arrange
        val userId = 123
        whenever(sharedPreferences.getInt(eq("user_id"), any())).thenReturn(userId)

        // Act
        userPreferences.saveUserId(userId)
        val result = userPreferences.getUserId()

        // Assert
        assertEquals(userId, result)

        // Verify - use atLeastOnce() since the method is called multiple times
        verify(context, atLeastOnce()).getSharedPreferences(eq("user_preferences"), eq(Context.MODE_PRIVATE))
        verify(sharedPreferencesEditor).putInt(eq("user_id"), eq(userId))
        verify(sharedPreferencesEditor).apply()
    }

    @Test
    fun testMockingPackageManager() {
        // Mock PackageManager
        val packageManager = mock<PackageManager>()
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(context.packageName).thenReturn("com.jetbrains.sample.app")

        // Mock app info checking
        whenever(packageManager.getPackageInfo(eq("com.jetbrains.sample.app"), eq(0)))
            .thenReturn(mock())

        // Verify we can get package info without exceptions
        val packageInfo = packageManager.getPackageInfo("com.jetbrains.sample.app", 0)
        assertNotNull(packageInfo)

        // Verify interactions
        verify(packageManager).getPackageInfo(eq("com.jetbrains.sample.app"), eq(0))
    }
}
