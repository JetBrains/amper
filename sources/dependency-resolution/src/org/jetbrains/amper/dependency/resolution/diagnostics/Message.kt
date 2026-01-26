/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.diagnostics

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.modules.SerializersModuleBuilder
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

    /**
     * Whether the message should be reported for transitive dependencies.
     */
    val reportTransitive: Boolean get() = true

    /**
     * @return false if the diagnostic points to the temporary issue
     * that might be resolved in the later resolution runs (i/o errors, network failures).
     * Diagnostic may point to such an issue itself, or it might be one of its nested (child) diagnostics
     * (of the same or higher severity level).
     * In any case, a dependency resolution graph that contains such diagnostics should not be reused
     * and should be recalculated the next time it is requested.
     */
    val cacheable: Boolean get() = true
}

val Message.detailedMessage: @Nls String
    get() = if (details == null) message else "$message\n$details"

/**
 * Allows having a hierarchy of messages (e.g., causes or suppresses).
 */
internal interface WithChildMessages : Message {
    val childMessages: List<Message>

    override val details: @Nls String? get() = if (childMessages.isEmpty()) null else nestedMessages()

    override val cacheable: Boolean
        get() = childMessages.all {
            // less severe suppressed diagnostics doesn't prevent us from caching this one
            it.severity < severity
                    || it.cacheable
        }
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
@Serializable
internal data class SimpleMessage(
    val text: @Nls String,
    val extra: @Nls String = "",
    override val severity: Severity = Severity.INFO,
    @Transient
    val throwable: Throwable? = null,
    private val preventCaching: Boolean = false,
    override val childMessages: List<Message> = emptyList(),
    override val id: String = "simple.message"
) : WithChildMessages {

    override val message: @Nls String
        get() = "${text}${extra.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}"

    override val cacheable: Boolean get() = !preventCaching && super.cacheable
}

internal fun SerializersModuleBuilder.registerSerializableMessages() {
    polymorphic(Message::class, SimpleMessage::class, SimpleMessage.serializer())
    polymorphic(Message::class, BomDeclaredAsRegularDependency::class, BomDeclaredAsRegularDependency.serializer())
    polymorphic(Message::class, RegularDependencyDeclaredAsBom::class, RegularDependencyDeclaredAsBom.serializer())
    polymorphic(Message::class, MetadataResolvedWithPomErrors::class, MetadataResolvedWithPomErrors.serializer())
    polymorphic(Message::class, PomResolvedWithMetadataErrors::class, PomResolvedWithMetadataErrors.serializer())
    polymorphic(Message::class, UnableToResolveDependency::class, UnableToResolveDependency.serializer())
    polymorphic(Message::class, UnableToDownloadChecksums::class, UnableToDownloadChecksums.serializer())
    polymorphic(Message::class, UnableToDownloadFile::class, UnableToDownloadFile.serializer())
    polymorphic(Message::class, PlatformsAreNotSupported::class, PlatformsAreNotSupported.serializer())
}

