import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverageTest {
    @Test
    fun doTest() {
        val covered = PartiallyCoveredClass()

        covered.covered()
        covered.conditional()
    }

    @Test
    fun worldTest() {
        assertEquals("World", World.get())
    }
}