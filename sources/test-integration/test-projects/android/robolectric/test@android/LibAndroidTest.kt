import kotlin.test.Test

class LibAndroidTest : PlatformSupport() {
    @Test
    fun doTest() {
        println(android.app.Application::class.simpleName) // make sure we can load this class
    }
}
