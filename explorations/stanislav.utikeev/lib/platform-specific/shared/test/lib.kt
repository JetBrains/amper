import kotlin.test.Test
import kotlin.test.assertEquals
import greet
import getPlatform

class LibTest {
    @Test
    fun `test greeting`() {
        assertTrue(getPlatform() in greet("Bob"))
    }
}
