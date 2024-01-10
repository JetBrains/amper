package apkg

import kotlin.test.Test
import kotlin.test.assertEquals

class ATest {
    @Test
    fun smoke() {
        assertEquals("Hello from src", MyObject.xxx)
        println("Hello from test method")
    }
}
