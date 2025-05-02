import kotlin.test.Test
import kotlin.test.assertTrue

class WorldTestFromMacOsCli {
    @Test
    fun doTest() {
        assertTrue(getWorld().contains("World"))
    }
}
