import kotlin.test.Test
import kotlin.test.assertTrue

class LibTest {
    @Test
    fun doTest() {
        assertTrue(Lib().get().contains("World"))
    }
}
