import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory
import org.tinylog.Level
import org.tinylog.Logger
import org.tinylog.jul.JulTinylogBridge
import kotlin.test.Test
import kotlin.test.assertEquals

class LogCollectorExtensionTest {
    @RegisterExtension
    val log = LogCollectorExtension()

    @BeforeEach
    fun before() {
        JulTinylogBridge.activate()
    }

    @Test
    fun slf4j() {
        LoggerFactory.getLogger(javaClass).info("slf4j entry")
        assertEntry(Level.INFO, "slf4j entry")
    }

    @Test
    fun tinylog() {
        Logger.warn("tinylog entry" as Any)
        assertEntry(Level.WARN, "tinylog entry")
    }

    @Test
    fun javaUtilsLogging() {
        java.util.logging.Logger.getLogger(javaClass.name).severe("jul entry")
        assertEntry(Level.ERROR, "jul entry")
    }

    private fun assertEntry(level: Level, message: String) {
        val entry = log.entries.single()
        assertEquals(level, entry.level)
        assertEquals(message, entry.message)
    }
}