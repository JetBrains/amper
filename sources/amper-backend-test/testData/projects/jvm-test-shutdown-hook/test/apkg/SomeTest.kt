package apkg

import kotlin.test.Test

class SomeTest {
    @Test
    fun smoke() {
        Runtime.getRuntime().addShutdownHook(Thread {
            SomeUserClass() // try to load a new class from the user classpath
            println("Hello from shutdown hook")
        })
    }
}
