import kotlin.test.Test
import kotlin.test.assertTrue

class LibTest : PlatformSupport() {
    @Test
    fun doTest() {
        assertTrue(getKmpText().contains("World"))
    }
}
