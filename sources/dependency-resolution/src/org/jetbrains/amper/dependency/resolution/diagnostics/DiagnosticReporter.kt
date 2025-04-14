/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.diagnostics

import java.util.concurrent.CopyOnWriteArrayList

internal interface DiagnosticReporter {
    fun addMessage(message: Message): Boolean
    fun addMessages(messages: List<Message>): Boolean
    fun getMessages(): List<Message>
    fun suppress(suppressMessageBuilder: (suppressedMessages: List<Message>) -> Message)
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

    override fun suppress(suppressMessageBuilder: (List<Message>) -> Message) {
        val suppressedMessages = diagnostics.toList()
        reset()
        diagnostics.add(suppressMessageBuilder(suppressedMessages))
    }

    override fun reset() {
        diagnostics.clear()
    }
}

internal fun DiagnosticReporter.hasErrors(): Boolean = getMessages().any { it.severity == Severity.ERROR }
