/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jic

import kotlinx.serialization.Serializable
import org.jetbrains.amper.serialization.paths.SerializablePath

/**
 * This special request data structure is used to pass data via the STDIN to the external process that
 * executes Java incremental compilation with the help of the JIC engine.
 *
 * The request is sent from the JvmCompileTask and is received by the `amper-jic-runner`.
 */
@Serializable
class JicCompilationRequest(
    val amperModuleName: String,
    val amperModuleDir: SerializablePath,
    val isTest: Boolean,
    val javaSourceFiles: List<SerializablePath>,
    val jicJavacArgs: List<String>,
    val javaCompilerOutputRoot: SerializablePath,
    val jicDataDir: SerializablePath,
    val classpath: List<SerializablePath>,
)
