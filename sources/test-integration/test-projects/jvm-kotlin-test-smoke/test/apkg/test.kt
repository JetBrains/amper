package apkg

import kotlin.test.Test
import kotlin.test.assertEquals

class ATest {
    @Test
    fun smoke() {
        // internal objects (MyObject, JavaUtils) must be accessible from test fragment
        assertEquals("Hello from src", MyObject.xxx)
        println("Hello from test method, ${JavaUtils.sss()}" )
    }
}
