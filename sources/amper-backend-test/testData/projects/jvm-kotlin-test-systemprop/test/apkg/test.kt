package apkg

import kotlin.test.Test
import kotlin.test.assertEquals

class ATest {
    @Test
    fun systemProp() {
        assertEquals("hello", System.getProperty("my.system.prop"))
    }
}
