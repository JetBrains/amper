package apkg

import Util
import kotlin.test.Test
import kotlin.test.assertEquals
import org.apache.commons.text.CaseUtils

class ATest {
    @Test
    fun smoke() {
        // dependencies of production fragment should be available in test
        val x = CaseUtils.toCamelCase("one two", true)
        println("FromExternalDependencies:$x FromProject:${Util.printFunc()}")
    }
}
