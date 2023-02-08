import kotlin.test.Test
import kotlin.test.assertEquals
import androidSpecificMethod

class LibTest {
    @Test
    fun `test android specific method`() {
        assertEquals(2, androidSpecificMethod(1))
    }
}
