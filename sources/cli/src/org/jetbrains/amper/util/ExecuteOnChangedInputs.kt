/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("BlockingMethodInNonBlockingContext", "LoggingStringTemplateAsArgument")

package org.jetbrains.amper.util

import com.google.common.hash.Hashing
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.concurrency.withReentrantLock
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.extract.readEntireFileToByteArray
import org.jetbrains.amper.core.extract.writeFully
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.useWithScope
import org.jetbrains.amper.diagnostics.setListAttribute
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileVisitResult
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString
import kotlin.io.path.readAttributes
import kotlin.io.path.visitFileTree
import kotlin.time.measureTimedValue

class ExecuteOnChangedInputs(
    buildOutputRoot: AmperBuildOutputRoot,
    private val currentAmperBuildNumber: String = AmperBuild.BuildNumber,
) {
    private val stateRoot = buildOutputRoot.path.resolve("incremental.state")

    // increment this counter if you change the state file format
    private val stateFileFormatVersion = 2

    /**
     * Executes the given [block] or returns an existing result from the incremental cache.
     *
     * ### Caching
     *
     * The previous result is immediately returned without executing [block] if all the following conditions are met:
     *  * the [configuration] map has not changed
     *  * the given set of [input][inputs] paths has not changed
     *  * the files located at the given [input][inputs] paths have not changed
     *  * the output files from the latest execution have not changed
     *  * the version of Amper that produced the cached result is the same as the current Amper version
     *
     * ### Concurrency
     *
     * The given [block] is always executed under double-locking based on the given [id], which means that 2 calls with
     * the same [id] cannot be executed at the same time by multiple threads or multiple Amper processes.
     * If one call needs to re-run [block] because the cache is invalid, subsequent calls with the same ID will suspend
     * until the first call completes and then resume and use the cache immediately (if possible).
     */
    suspend fun execute(
        id: String,
        configuration: Map<String, String>,
        inputs: List<Path>,
        block: suspend () -> ExecutionResult
    ): ExecutionResult = spanBuilder("inc $id")
        .setListAttribute("configuration", configuration.entries.map { "${it.key}=${it.value}" }.sorted())
        .setListAttribute("inputs", inputs.map { it.pathString }.sorted())
        .useWithScope { span ->

        stateRoot.createDirectories()

        // hash includes stateFileFormatVersion to automatically use a different file if the file format was changed
        val stateFile = stateRoot.resolve(
            id.replace(Regex("[^a-zA-Z0-9]"), "_") +
                    "-" + Hashing.sha256().hashString("$id\nstate format version: $stateFileFormatVersion", Charsets.UTF_8).toString().take(10))
        // Prevent parallel execution of this 'id' from this or other processes,
        // tracked by a lock on state file
        withLock(id, stateFile) { stateFileChannel ->
            val (existingResult, cacheCheckTime) = measureTimedValue {
                getCachedResult(stateFile, stateFileChannel, configuration, inputs)
            }
            if (existingResult != null) {
                logger.debug("INC: up-to-date according to state file at '{}' in {}", stateFile, cacheCheckTime)
                logger.info("'$id' is up-to-date")
                span.setAttribute("status", "up-to-date")
                addResultToSpan(span, existingResult)
                return@withLock existingResult
            } else {
                span.setAttribute("status", "requires-building")
                logger.info("building '$id'")
            }

            val (result, buildTime) = measureTimedValue { block() }

            addResultToSpan(span, result)

            logger.info("finished '$id' in $buildTime")

            writeStateFile(stateFileChannel, configuration, inputs, result)

            // TODO remove this check later or hide under debug/assert mode
            ensureStateFileIsConsistent(stateFile, stateFileChannel, configuration, inputs, result)

            return@withLock result
        }
    }

    private fun addResultToSpan(span: Span, result: ExecutionResult) {
        span.setListAttribute("outputs", result.outputs.map { it.pathString }.sorted())
        span.setListAttribute("output-properties", result.outputProperties.map { "${it.key}=${it.value}" }.sorted())
    }

    private fun writeStateFile(stateFileChannel: FileChannel, configuration: Map<String, String>, inputs: List<Path>, result: ExecutionResult) {
        val state = State(
            amperBuild = currentAmperBuildNumber,
            configuration = configuration,
            inputs = inputs.map { it.pathString }.toSet(),
            inputsState = getPathListState(inputs, failOnMissing = false),
            outputs = result.outputs.map { it.pathString }.toSet(),
            outputsState = getPathListState(result.outputs, failOnMissing = true),
            outputProperties = result.outputProperties,
        )

        stateFileChannel.truncate(0)
        stateFileChannel.writeFully(ByteBuffer.wrap(jsonSerializer.encodeToString(state).toByteArray()))
    }

    object SortedMapSerializer: KSerializer<Map<String, String>> {
        private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

        override val descriptor: SerialDescriptor = mapSerializer.descriptor

        override fun serialize(encoder: Encoder, value: Map<String, String>) {
            mapSerializer.serialize(encoder, value.toSortedMap())
        }

        override fun deserialize(decoder: Decoder): Map<String, String> {
            return mapSerializer.deserialize(decoder)
        }
    }

    @Serializable
    private data class State(
        val amperBuild: String,
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
    )

    private fun ensureStateFileIsConsistent(
        stateFile: Path,
        stateFileChannel: FileChannel,
        configuration: Map<String, String>,
        inputs: List<Path>,
        result: ExecutionResult,
    ) {
        try {
            val r = getCachedResult(stateFile, stateFileChannel, configuration, inputs)
                ?: run {
                    stateFileChannel.position(0)
                    val stateText = stateFileChannel.readEntireFileToByteArray().decodeToString()
                    error("Not up-to-date after successfully writing a state file: $stateFile\n" +
                         "--- BEGIN $stateFile\n" +
                         stateText.ensureEndsWith("\n") +
                         "--- END $stateFile")
                }

            if (r.outputs != result.outputs) {
                error(
                    "Outputs list mismatch: $stateFile:\n" +
                            "1: ${r.outputs}\n" +
                            "2: ${result.outputs}"
                )
            }

            if (r.outputProperties != result.outputProperties) {
                error(
                    "Output properties mismatch: $stateFile:\n" +
                            "1: ${r.outputProperties.map { "${it.key}=${it.value}" }.sorted()}\n" +
                            "2: ${result.outputProperties.map { "${it.key}=${it.value}" }.sorted()}"
                )
            }
        } catch (t: Throwable) {
            stateFile.deleteIfExists()
            throw t
        }
    }

    // TODO Probably rewrite to JSON? or a binary format?
    private fun getCachedResult(stateFile: Path, stateFileChannel: FileChannel, configuration: Map<String, String>, inputs: List<Path>): ExecutionResult? {
        if (stateFileChannel.size() <= 0) {
            logger.debug("INC: state file is missing or empty at '{}' -> rebuilding", stateFile)
            return null
        }

        val stateText = try {
            stateFileChannel.position(0)
            stateFileChannel.readEntireFileToByteArray().decodeToString()
        } catch (t: Throwable) {
            logger.warn("INC: Unable to read state file '$stateFile' -> rebuilding", t)
            return null
        }

        if (stateText.isBlank()) {
            logger.warn("INC: Previous state file '$stateFile' is empty -> rebuilding")
            return null
        }

        val state = try {
            jsonSerializer.decodeFromString<State>(stateText)
        } catch (t: Throwable) {
            logger.warn("INC: Unable to deserialize state file '$stateFile' -> rebuilding", t)
            return null
        }

        if (state.amperBuild != currentAmperBuildNumber) {
            logger.info(
                "INC: State file '$stateFile' has a different Amper build number -> rebuilding\n" +
                        "old: ${state.amperBuild}\n" +
                        "current: $currentAmperBuildNumber"
            )
            return null
        }

        if (state.configuration != configuration) {
            // TODO better reporting what was exactly changed
            logger.debug(
                "INC: state file has a wrong configuration at '$stateFile' -> rebuilding\n" +
                        "  old: ${state.configuration}\n" +
                        "  new: $configuration"
            )
            return null
        }

        val inputPaths = inputs.map { it.pathString }.toSet()
        if (state.inputs != inputPaths) {
            logger.debug(
                "INC: state file has a wrong inputs list at '$stateFile' -> rebuilding\n" +
                        "  old: ${state.inputs.sorted()}\n" +
                        "  new: ${inputPaths.sorted()}"
            )
            return null
        }

        val currentInputsState = getPathListState(inputs, failOnMissing = false)
        if (state.inputsState != currentInputsState) {
            logger.debug(
                "INC: state file has a wrong inputs at '$stateFile' -> rebuilding\n" +
                        "  old: ${state.inputsState}\n" +
                        "  new: $currentInputsState"
            )
            return null
        }

        val outputsList = state.outputs.map { Path(it) }
        val currentOutputsState = getPathListState(outputsList, failOnMissing = false)
        if (state.outputsState != currentOutputsState) {
            logger.debug(
                "INC: state file has a wrong outputs at '$stateFile' -> rebuilding\n" +
                        "  old: ${state.outputsState}\n" +
                        "  new: $currentOutputsState"
            )
            return null
        }

        return ExecutionResult(outputs = outputsList, outputProperties = state.outputProperties)
    }

    private fun getPathListState(paths: List<Path>, failOnMissing: Boolean): Map<String, String> {
        val files = mutableMapOf<String, String>()

        fun addFile(path: Path, attr: BasicFileAttributes?) {
            if (attr == null) {
                if (failOnMissing) {
                    throw NoSuchFileException(file = path.toFile(), reason = "path from outputs is not found")
                } else {
                    files[path.pathString] = "MISSING"
                }
            } else {
                val posixPart = if (attr is PosixFileAttributes) {
                    " mode ${PosixUtil.toUnixMode(attr.permissions())} owner ${attr.owner().name} group ${attr.group().name}"
                } else ""
                files[path.pathString] = "size ${attr.size()} mtime ${attr.lastModifiedTime()}$posixPart"
            }
        }

        for (path in paths) {
            check(path.isAbsolute) {
                "Path must be absolute: $path"
            }

            val attr: BasicFileAttributes? = getAttributes(path)
            if (attr?.isDirectory == true) {
                // TODO this walk could be multi-threaded, it's trivial to implement with coroutines

                fun processDirectory(current: Path) {
                    var childrenCount = 0

                    // Using Path.visitFileTree to get both file name AND file attributes at the same time.
                    // This is much faster on OSes where you can get both, e.g., Windows.
                    current.visitFileTree {
                        onPreVisitDirectory { subdir, attrs ->
                            if (current == subdir) {
                                return@onPreVisitDirectory FileVisitResult.CONTINUE
                            }

                            childrenCount += 1
                            processDirectory(subdir)
                            FileVisitResult.SKIP_SUBTREE
                        }

                        onVisitFile { file, attrs ->
                            childrenCount += 1

                            addFile(file, attrs)

                            FileVisitResult.CONTINUE
                        }

                        onPostVisitDirectory { dir, exc ->
                            if (exc != null) {
                                throw exc
                            }
                            FileVisitResult.CONTINUE
                        }
                    }

                    if (childrenCount == 0) {
                        files[current.pathString] = "EMPTY DIR"
                    }
                }

                processDirectory(path)
            } else {
                addFile(path, attr)
            }
        }

        return files
    }

    private fun getAttributes(path: Path): BasicFileAttributes? {
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
        return attr
    }

    class ExecutionResult(val outputs: List<Path>, val outputProperties: Map<String, String> = emptyMap())

    companion object {
        private val logger = LoggerFactory.getLogger(ExecuteOnChangedInputs::class.java)

        private val jsonSerializer = Json {
            prettyPrint = true
        }

        private val executeOnChangedLocks = ConcurrentHashMap<String, Mutex>()

        private suspend fun <R> withLock(id: String, stateFile: Path, block: suspend (FileChannel) -> R): R {
            val mutex = executeOnChangedLocks.computeIfAbsent(id) { Mutex() }
            return mutex.withReentrantLock {
                FileChannel.open(stateFile, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
                    .use { fileChannel ->
                        fileChannel.lock().use {
                            block(fileChannel)
                        }
                    }
            }
        }
    }
}
