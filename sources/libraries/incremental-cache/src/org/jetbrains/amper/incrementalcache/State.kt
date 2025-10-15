/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.incrementalcache

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jetbrains.amper.filechannels.readText
import org.jetbrains.amper.filechannels.writeText
import org.slf4j.LoggerFactory
import java.nio.channels.FileChannel
import java.nio.file.Path
import kotlin.time.Instant

private val logger = LoggerFactory.getLogger(State::class.java)

private val jsonSerializer = Json {
    // we want to be able to examine state files
    prettyPrint = true
}

@Serializable
internal data class State(
    /**
     * Represents the identity of the code being executed. It should change when the cached logic changes.
     * The cache will be discarded and rebuilt if it was stored using a different [codeVersion].
     *
     * One suitable option for this [codeVersion] is to use the hash of the currently running code.
     * The [computeClassPathHash] helper provides a way to create a hash of all jars on the classpath.
     */
    val codeVersion: String,
    /**
     * Some key-value pairs used as input to the cached computation.
     */
    @Serializable(with = SortedMapSerializer::class)
    val inputValues: Map<String, String>,
    /**
     * Some input files used in the cached computation.
     */
    val inputFiles: Set<String>,
    /**
     * The state of each input file, in terms of size, modification time, and permissions.
     */
    @Serializable(with = SortedMapSerializer::class)
    val inputFilesState: Map<String, String>,
    /**
     * Some key-value pairs produced as output of the cached computation.
     */
    @Serializable(with = SortedMapSerializer::class)
    val outputValues: Map<String, String>,
    /**
     * Some output files produced by the cached computation.
     */
    val outputFiles: Set<String>,
    /**
     * The state of each output file, in terms of size, modification time, and permissions.
     */
    @Serializable(with = SortedMapSerializer::class)
    val outputFilesState: Map<String, String>,
    /**
     * A subset of [outputFiles] that should not be considered for cache invalidation.
     */
    val excludedOutputFiles: Set<String>,
    /**
     * Date and time the cache entry is no longer valid after, and should be recalculated
     */
    @Serializable(with = InstantSerializer::class)
    val expirationTime: Instant? = null,
) {
    companion object {
        // increment this counter if you change the state file format
        const val formatVersion = 5
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

internal object InstantSerializer: KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("kotlin.time.Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
}
