import kotlin.test.Test
import kotlin.test.assertEquals
import greet

class LibTest {
    @Test
    fun `test greeting`() {
        assertEquals("Hello, Bob", greet("Bob"))
    }
}
