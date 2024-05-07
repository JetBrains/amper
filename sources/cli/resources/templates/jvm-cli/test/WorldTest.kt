import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldTest {
    @Test
    fun doTest() {
        assertEquals("World", World.get())
    }

    @Test
    fun shouldFail() {
        assertTrue(false)
    }
}