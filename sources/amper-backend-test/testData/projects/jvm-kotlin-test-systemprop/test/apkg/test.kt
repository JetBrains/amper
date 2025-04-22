package apkg

import java.lang.management.ManagementFactory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class ATest {
    @Test
    fun systemProp() {
        // Options declared in the module.yaml
        assertContains(ManagementFactory.getRuntimeMXBean().inputArguments, "-Xmx1G")
        assertEquals("hello", System.getProperty("my.system.prop"))

        // Options that are expected to be passed via cli
        assertEquals("foo", System.getProperty("my.declared.system.prop"))
    }
}
