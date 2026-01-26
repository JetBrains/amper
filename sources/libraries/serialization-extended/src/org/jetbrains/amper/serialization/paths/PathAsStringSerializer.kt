/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.serialization.paths

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

/**
 * A regular [Path] type, but configured for serialization with [PathAsStringSerializer].
 */
typealias SerializablePath = @Serializable(with = PathAsStringSerializer::class) Path

/**
 * A serializer to serialize [Path]s as a simple string.
 * The string value is simply the OS-dependent string representation of the path ([Path.pathString]).
 */
object PathAsStringSerializer : KSerializer<Path> {
    override val descriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.pathString)
    override fun deserialize(decoder: Decoder): Path = Path(decoder.decodeString())
}
