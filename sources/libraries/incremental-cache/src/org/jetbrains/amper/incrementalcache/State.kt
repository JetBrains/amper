/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.incrementalcache

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jetbrains.amper.filechannels.readText
import org.jetbrains.amper.filechannels.writeText
import org.slf4j.LoggerFactory
import java.nio.channels.FileChannel
import java.nio.file.Path

private val logger = LoggerFactory.getLogger(State::class.java)

private val jsonSerializer = Json {
    // we want to be able to examine state files
    prettyPrint = true
}

@Serializable
internal data class State(
    val codeVersion: String,
    @Serializable(with = SortedMapSerializer::class)
    val configuration: Map<String, String>,
    val inputs: Set<String>,
    @Serializable(with = SortedMapSerializer::class)
    val inputsState: Map<String, String>,
    val outputs: Set<String>,
    @Serializable(with = SortedMapSerializer::class)
    val outputsState: Map<String, String>,
    @Serializable(with = SortedMapSerializer::class)
    val outputProperties: Map<String, String>,
) {
    companion object {
        // increment this counter if you change the state file format
        const val formatVersion = 3
    }
}

internal fun FileChannel.writeState(state: State) {
    truncate(0)
    writeText(jsonSerializer.encodeToString(state))
}

internal fun FileChannel.readState(pathForLogs: Path): State? {
    if (size() <= 0) {
        logger.debug("[inc] state file is missing or empty at '{}' -> cache miss", pathForLogs)
        return null
    }

    val stateText = try {
        position(0)
        readText()
    } catch (t: Throwable) {
        logger.warn("[inc] Unable to read state file '$pathForLogs' -> cache miss", t)
        return null
    }

    if (stateText.isBlank()) {
        logger.warn("[inc] Previous state file '$pathForLogs' is empty -> cache miss")
        return null
    }

    return try {
        jsonSerializer.decodeFromString<State>(stateText)
    } catch (t: Throwable) {
        logger.warn("[inc] Unable to deserialize state file '$pathForLogs' -> cache miss", t)
        return null
    }
}

private object SortedMapSerializer: KSerializer<Map<String, String>> {
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    override val descriptor: SerialDescriptor = mapSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, String>) {
        mapSerializer.serialize(encoder, value.toSortedMap())
    }

    override fun deserialize(decoder: Decoder): Map<String, String> {
        return mapSerializer.deserialize(decoder)
    }
}
