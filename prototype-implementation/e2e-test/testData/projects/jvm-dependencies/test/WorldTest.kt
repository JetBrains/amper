import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldTest {
    @Test
    fun doDependency() {
        // test that main common dependency accessible in tests
        val year = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
        assertTrue(year > 2020)
    }
}