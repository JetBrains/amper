import org.junit.Test
import kotlin.test.assertEquals

class WorldTest {
    @Test
    fun doTest() {
        assertEquals("Unknown World", World.get())
    }
}