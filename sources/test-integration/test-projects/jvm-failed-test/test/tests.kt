import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FailedTest {
    @Test
    fun doTest() {
        assertTrue(true)
    }

    @Test
    fun booleanFailure() {
        assertTrue(false, "The boolean value is incorrect")
    }

    @Test
    fun stringComparisonFailure() {
        assertEquals(expected = "EXPECTED_VALUE", actual = "ACTUAL_VALUE", message = "Strings are not equal")
    }
}