import kotlin.test.Test
import kotlin.test.assertEquals

class JvmWorldTest {
    @Test
    fun doTest() {
        assertEquals("JVM World", World().get())
    }
}