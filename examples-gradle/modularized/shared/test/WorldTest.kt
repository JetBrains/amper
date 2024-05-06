import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorldTest {
    @Test
    fun doTest() {
        assertTrue(World().get().contains("World"))
    }
}