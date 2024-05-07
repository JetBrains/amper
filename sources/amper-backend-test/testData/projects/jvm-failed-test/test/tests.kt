import kotlin.test.Test
import kotlin.test.assertTrue

class FailedTest {
    @Test
    fun doTest() {
        assertTrue(true)
    }

    @Test
    fun shouldFail() {
        assertTrue(false)
    }
}