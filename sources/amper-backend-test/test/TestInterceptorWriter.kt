import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.AbstractWriter

class TestInterceptorWriter(properties: MutableMap<String, String>) : AbstractWriter(properties) {
    override fun getRequiredLogEntryValues(): Collection<LogEntryValue> =
        LogEntryValue.entries.toList()

    override fun write(logEntry: LogEntry) {
        listeners.forEach { it.onLogEntry(logEntry) }
    }

    override fun flush() {}

    override fun close() {}

    companion object {
        private var listeners: Set<Listener> = emptySet()

        fun addListener(listener: Listener) = synchronized(this) {
            check(!listeners.contains(listener))
            listeners = listeners + listener
        }

        fun removeListener(listener: Listener) = synchronized(this) {
            check(listeners.contains(listener))
            listeners = listeners - listener
        }
    }

    interface Listener {
        fun onLogEntry(logEntry: LogEntry)
    }
}
