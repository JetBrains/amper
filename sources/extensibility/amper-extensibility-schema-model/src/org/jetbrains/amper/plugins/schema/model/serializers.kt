/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.schema.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path
import kotlin.io.path.Path

typealias PathAsString = @Serializable(with = PathSerializer::class) Path

object PathSerializer : KSerializer<Path> {
    override val descriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.toAbsolutePath().toString())
    override fun deserialize(decoder: Decoder): Path = Path(decoder.decodeString())
}

object RangeSerializer : KSerializer<IntRange> {
    override val descriptor: SerialDescriptor
        get() = SerialDescriptor("kotlin.ranges.IntRange", IntRangeSurrogate.serializer().descriptor)

    override fun serialize(encoder: Encoder, value: IntRange) {
        encoder.encodeSerializableValue(IntRangeSurrogate.serializer(), IntRangeSurrogate(value.first, value.last))
    }

    override fun deserialize(decoder: Decoder): IntRange {
        return decoder.decodeSerializableValue(IntRangeSurrogate.serializer()).let { it.first..it.last }
    }

    @Serializable data class IntRangeSurrogate(
        val first: Int, val last: Int,
    )
}
