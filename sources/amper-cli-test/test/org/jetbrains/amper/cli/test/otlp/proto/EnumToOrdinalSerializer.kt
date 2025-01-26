/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test.otlp.proto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.enums.EnumEntries

open class EnumToOrdinalSerializer<E : Enum<E>>(private val entries: EnumEntries<E>) : KSerializer<E> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EnumToOrdinalSerializer", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): E = entries[decoder.decodeInt()]

    override fun serialize(encoder: Encoder, value: E) {
        encoder.encodeInt(value.ordinal)
    }
}