/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.diagnostics

import org.jetbrains.annotations.Nls

/**
 * Designates a type of errors reported during the dependency resolution.
 *
 * Prefer using typed messages instead of [SimpleMessage].
 */
interface Message {
    val id: String
    val severity: Severity

    /**
     * A general one-line message.
     */
    val message: @Nls String

    /**
     * Will be used if the message is reported as a child.
     */
    val shortMessage: @Nls String get() = message

    /**
     * Additional details that will be shown in the IDE tooltip or appended after the [message].
     */
    val details: @Nls String? get() = null
}

val Message.detailedMessage: @Nls String
    get() = if (details == null) message else "$message\n$details"

/**
 * Allows having a hierarchy of messages (e.g., causes or suppresses).
 */
internal interface WithChildMessages : Message {
    val childMessages: List<Message>

    override val details: @Nls String? get() = if (childMessages.isEmpty()) null else nestedMessages()
}

private fun WithChildMessages.nestedMessages(level: Int = 1): @Nls String = buildString {
    var first = true
    for (childMessage in childMessages) {
        if (childMessage.severity >= severity) {
            if (!first) appendLine() else first = false
            append("  ".repeat(level))
            append(childMessage.shortMessage)
            if (childMessage is WithChildMessages) {
                append(childMessage.nestedMessages(level + 1))
            }
        }
    }
}

/**
 * Marks that the [Message] could've been caused by a third-party error or exception.
 */
interface WithThrowable : Message {
    val throwable: Throwable?
}

enum class Severity {
    /**
     * Use for information that might provide additional insights on how the node was resolved.
     */
    INFO,

    /**
     * Use for information that marks that the result of resolution on the node is most likely unexpected.
     */
    WARNING,

    /**
     * Use for information that marks that the resolution of the given node failed, and it can't be used.
     */
    ERROR,
}

/**
 * A simple implementation of the [Message] that doesn't hold any additional semantics.
 *
 * It's better to have a strongly typed message for the better IDE integration.
 */
internal data class SimpleMessage(
    val text: @Nls String,
    val extra: @Nls String = "",
    override val severity: Severity = Severity.INFO,
    override val throwable: Throwable? = null,
    override val childMessages: List<Message> = emptyList(),
    override val id: String = "simple.message"
) : WithChildMessages, WithThrowable {

    override val message: @Nls String
        get() = "${text}${extra.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}"
}
