/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("BlockingMethodInNonBlockingContext")

package org.jetbrains.amper.util

import com.google.common.hash.Hashing
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.bufferedReader
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.readAttributes
import kotlin.io.path.walk
import kotlin.time.measureTimedValue

class ExecuteOnChangedInputs(buildOutputRoot: AmperBuildOutputRoot) {
    private val stateRoot = buildOutputRoot.path.resolve("incremental.state")
    private val stateFileFormatVersion = 1

    suspend fun execute(
        id: String,
        configuration: Map<String, String>,
        inputs: List<Path>,
        block: suspend () -> ExecutionResult
    ): ExecutionResult {
        Files.createDirectories(stateRoot)
        val stateFile = stateRoot.resolve(
            id.replace(Regex("[^a-zA-Z0-9]"), "_") +
                    "-" + Hashing.sha256().hashString(id, Charsets.UTF_8).toString().take(10))

        val (existingResult, cacheCheckTime) = measureTimedValue {
            getCachedResult(stateFile, configuration, inputs)
        }
        if (existingResult != null) {
            logger.info("INC: up-to-date according to state file at '$stateFile' in $cacheCheckTime")
            return existingResult
        }

        val result = block()

        writeStateFile(id, stateFile, configuration, inputs, result)

        // TODO remove this check later or hide under debug/assert mode
        ensureStateFileIsConsistent(stateFile, configuration, inputs, result)

        return result
    }

    private fun writeStateFile(id: String, stateFile: Path, configuration: Map<String, String>, inputs: List<Path>, result: ExecutionResult) {
        val properties = Properties()

        properties["version"] = stateFileFormatVersion.toString()
        properties["configuration"] = configuration.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }
        properties["inputs.list"] = inputs.sorted().joinToString("\n")
        properties["inputs"] = getPathListState(inputs)
        properties["outputs.list"] = result.outputs.joinToString("\n")
        properties["outputs"] = getPathListState(result.outputs)

        stateFile.outputStream().buffered().use { properties.store(it, id) }
    }

    private fun ensureStateFileIsConsistent(
        stateFile: Path,
        configuration: Map<String, String>,
        inputs: List<Path>,
        result: ExecutionResult
    ) {
        try {
            val r = getCachedResult(stateFile, configuration, inputs)
                ?: error("Not up-to-date after successfully writing a state file: $stateFile")

            if (r.outputs != result.outputs) {
                error(
                    "Outputs list mismatch: $stateFile:\n" +
                            "1: ${r.outputs}\n" +
                            "2: ${result.outputs}"
                )
            }
        } catch (t: IllegalStateException) {
            Files.deleteIfExists(stateFile)
            throw t
        }
    }

    // TODO Probably rewrite to JSON? or a binary format?
    private fun getCachedResult(stateFile: Path, configuration: Map<String, String>, inputs: List<Path>): ExecutionResult? {
        if (!stateFile.isRegularFile()) {
            logger.info("INC: state file is missing at '$stateFile' -> rebuilding")
            return null
        }

        val properties = Properties()
        stateFile.bufferedReader().use { properties.load(it) }
        if (properties.getProperty("version") != stateFileFormatVersion.toString()) {
            logger.info("INC: state file has a wrong version at '$stateFile' -> rebuilding")
            return null
        }

        val oldConfiguration = properties.getProperty("configuration")
        val newConfiguration = configuration.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }
        if (oldConfiguration != newConfiguration) {
            logger.info(
                "INC: state file has a wrong configuration at '$stateFile' -> rebuilding\n" +
                        "  old: ${oldConfiguration}\n" +
                        "  new: $newConfiguration"
            )
            return null
        }

        val oldInputsList = properties.getProperty("inputs.list")
        val newInputsList = inputs.sorted().joinToString("\n")
        if (oldInputsList != newInputsList) {
            logger.info(
                "INC: state file has a wrong inputs list at '$stateFile' -> rebuilding\n" +
                        "  old: ${oldInputsList}\n" +
                        "  new: $newInputsList"
            )
            return null
        }

        val oldInputs = properties.getProperty("inputs")
        val newInputs = getPathListState(inputs)
        if (oldInputs != newInputs) {
            logger.info(
                "INC: state file has a wrong inputs at '$stateFile' -> rebuilding\n" +
                        "  old: ${oldInputs}\n" +
                        "  new: $newInputs"
            )
            return null
        }

        val outputsList = (properties.getProperty("outputs.list") ?: "").split("\n").map { Path.of(it) }
        val oldOutputs = properties.getProperty("outputs")
        val newOutputs = getPathListState(outputsList)
        if (oldOutputs != newOutputs) {
            logger.info(
                "INC: state file has a wrong outputs at '$stateFile' -> rebuilding\n" +
                        "  old: ${oldOutputs}\n" +
                        "  new: $newOutputs"
            )
            return null
        }

        return ExecutionResult(outputs = outputsList)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun getPathListState(paths: List<Path>): String {
        val lines = mutableListOf<String>()

        fun addFile(path: Path, attr: BasicFileAttributes?) {
            if (attr == null) {
                lines.add("$path MISSING")
            } else {
                val posixPart = if (attr is PosixFileAttributes) {
                    " mode ${PosixUtil.toUnixMode(attr.permissions())} owner ${attr.owner().name} group ${attr.group().name}"
                } else ""
                lines.add("$path size ${attr.size()} mtime ${attr.lastModifiedTime()}$posixPart")
            }
        }

        for (path in paths) {
            // we assume that missing files is exceptional and usually all paths exist
            val attr: BasicFileAttributes? = try {
                if (PosixUtil.isPosixFileSystem) {
                    path.readAttributes<PosixFileAttributes>()
                } else {
                    path.readAttributes<BasicFileAttributes>()
                }
            } catch (e: NoSuchFileException) {
                null
            }

            if (attr?.isDirectory == true) {
                // TODO this walk could be multi-threaded, it's trivial to implement with coroutines
                for (sub in path.walk()) {
                    addFile(sub, attr)
                }
            } else {
                addFile(path, attr)
            }
        }

        return lines.sorted().joinToString("\n")
    }

    class ExecutionResult(val outputs: List<Path>, outputProperties: Map<String, String> = emptyMap()) {
        init {
            if (outputProperties.isNotEmpty()) {
                throw NotImplementedError("outputProperties are not yet implemented")
            }
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
