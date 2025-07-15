/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jic

import kotlinx.serialization.Contextual
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
 * This special request data structure is used to pass data via the STDIN to the external process that
 * executes Java incremental compilation with the help of the JIC engine.
 *
 * The request is sent from the JvmCompileTask and is received by the `amper-jic-runner`.
 */
@Serializable
class JicCompilationRequest(
    val amperModuleName: String,
    val amperModuleDir: PathAsString,
    val isTest: Boolean,
    val javaSourceFiles: List<PathAsString>,
    val jicJavacArgs: List<String>,
    val javaCompilerOutputRoot: PathAsString,
    val jicDataDir: PathAsString,
    val classpath: List<PathAsString>,
)

typealias PathAsString = @Contextual @Serializable(PathSerializer::class) Path

object PathSerializer : KSerializer<Path> {
    override val descriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeString(value.pathString)
    }
    override fun deserialize(decoder: Decoder): Path = Path(decoder.decodeString())
}
