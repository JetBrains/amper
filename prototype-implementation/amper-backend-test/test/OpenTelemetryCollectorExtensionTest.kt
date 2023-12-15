import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenTelemetryCollectorExtensionTest {
    @RegisterExtension
    val openTelemetryCollector = OpenTelemetryCollectorExtension()

    @Test
    fun simpleSpans() {
        runBlocking {
            spanBuilder("span1").setAttribute("key1", "value1").useWithScope {
                spanBuilder("span2").setAttribute("key2", "value2").useWithScope {
                    it.addEvent("x")
                }
            }
        }

        val spans = openTelemetryCollector.spans.sortedBy { it.name }
        assertEquals(2, spans.size)
        val (span1, span2) = spans

        assertEquals("span1", span1.name)
        assertEquals("key1=value1", span1.attributes.asMap().map { "${it.key}=${it.value}" }.joinToString(" "))

        assertEquals("span2", span2.name)
        assertEquals("key2=value2", span2.attributes.asMap().map { "${it.key}=${it.value}" }.joinToString(" "))

        assertEquals(span1.spanId, span2.parentSpanId)
    }

    @Test
    fun reset() {
        runBlocking {
            spanBuilder("span1").setAttribute("key1", "value1").useWithScope {
                spanBuilder("span2").setAttribute("key2", "value2").useWithScope {
                    it.addEvent("x")
                }
            }
        }
        assertEquals(2, openTelemetryCollector.spans.size)
        openTelemetryCollector.reset()
        assertEquals(0, openTelemetryCollector.spans.size)
    }
}