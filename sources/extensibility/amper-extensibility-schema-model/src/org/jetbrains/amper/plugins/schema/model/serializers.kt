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

class SchemaNameSerializer : KSerializer<PluginData.SchemaName> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SchemaName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PluginData.SchemaName) {
        val parts = buildList { add(value.packageName); addAll(value.simpleNames) }
        encoder.encodeString(parts.joinToString("/"))
    }

    override fun deserialize(decoder: Decoder): PluginData.SchemaName {
        val parts = decoder.decodeString().split("/")
        return PluginData.SchemaName(parts[0], parts.drop(1))
    }
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
