/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.diagnostics

import java.util.concurrent.CopyOnWriteArrayList

internal interface DiagnosticReporter {
    fun addMessage(message: Message): Boolean
    fun addMessages(messages: List<Message>): Boolean
    fun getMessages(): List<Message>
    fun suppress(message: SuppressingMessage): Boolean
    fun reset()
}

internal class CollectingDiagnosticReporter : DiagnosticReporter {
    private val diagnostics: MutableList<Message> = CopyOnWriteArrayList()

    override fun addMessage(message: Message): Boolean {
        return diagnostics.add(message)
    }

    override fun addMessages(messages: List<Message>): Boolean {
        return diagnostics.addAll(messages)
    }

    override fun getMessages() = diagnostics.toList()

    override fun suppress(message: SuppressingMessage): Boolean {
        val suppressedDiagnostics = diagnostics.toList()
        reset()
        return diagnostics.add(message.withSuppressed(messages = suppressedDiagnostics))
    }

    override fun reset() {
        diagnostics.clear()
    }
}

internal fun DiagnosticReporter.hasErrors(): Boolean = getMessages().any { it.severity == Severity.ERROR }
