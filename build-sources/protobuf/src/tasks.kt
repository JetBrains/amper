/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.protobuf

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.io.path.walk

@TaskAction
fun provisionBinaries(
    settings: Settings,
    @Output bin: Path,
) {
    val protocVersion = settings.protocVersion
    val grpcVersion = settings.grpc.version.takeIf { settings.grpc.enabled }

    bin.createDirectories()

    context(SystemInfo.detect()) {
        downloadBinary("com.google.protobuf", "protoc", protocVersion, to = bin / PROTOC_BINARY)
        if (grpcVersion != null) {
            downloadBinary("io.grpc", "protoc-gen-grpc-java", grpcVersion, to = bin / PROTOC_GEN_GRPC_JAVA_EXE)
        }
    }
}

@TaskAction
fun generateProto(
    @Input bin: Path,
    @Input sourceDir: Path,
    grpcEnabled: Boolean,
    @Output javaOutputDir: Path,
    @Output grpcOutputDir: Path,
) {
    val protoc = bin / PROTOC_BINARY
    val grpcBin = bin / PROTOC_GEN_GRPC_JAVA_EXE

    javaOutputDir.apply {
        deleteRecursively()
        createDirectories()
    }
    grpcOutputDir.apply {
        deleteRecursively()
        createDirectories()
    }

    val protoFiles = sourceDir.walk().filter { it.extension == "proto" }.sorted().toList()
    if (protoFiles.isEmpty()) {
        return
    }

    val commandLine = buildList {
        add(protoc.pathString)
        add("--java_out=$javaOutputDir")

        if (grpcEnabled) {
            add("--plugin=protoc-gen-grpc=$grpcBin")
            add("--grpc_out=$grpcOutputDir")
        }

        add("-I$sourceDir")

        addAll(protoFiles.map { it.pathString })
    }

    // FIXME: Introduce a process launching facility
    ProcessBuilder(commandLine)
        .inheritIO()
        .start()
        .waitFor()
        .let {
            check(it == 0) {
                "protoc terminated with code = $it. See the log for the errors"
            }
        }
}

private const val PROTOC_BINARY = "protoc.exe"
private const val PROTOC_GEN_GRPC_JAVA_EXE = "protoc-gen-grpc-java.exe"