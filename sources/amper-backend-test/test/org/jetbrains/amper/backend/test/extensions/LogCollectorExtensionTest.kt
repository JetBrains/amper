/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test.extensions

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

    @Test
    fun reset() {
        Logger.warn("tinylog entry1" as Any)
        Logger.warn("tinylog entry2" as Any)
        assertEquals(2, log.entries.size)
        log.reset()

        Logger.warn("tinylog entry3" as Any)
        assertEntry(Level.WARN, "tinylog entry3")
    }

    private fun assertEntry(level: Level, message: String) {
        val entry = when (log.entries.size) {
            0 -> error("No entries")
            1 -> log.entries.single()
            else -> error("Multiple entries, but only one is expected:\n" +
                    log.entries.joinToString("\n") { it.message })
        }
        assertEquals(level, entry.level)
        assertEquals(message, entry.message)
    }
}
