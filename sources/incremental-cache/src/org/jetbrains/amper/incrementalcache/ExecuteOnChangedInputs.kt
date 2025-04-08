/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("BlockingMethodInNonBlockingContext", "LoggingStringTemplateAsArgument")

package org.jetbrains.amper.incrementalcache

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
import org.jetbrains.amper.concurrency.withReentrantLock
import org.jetbrains.amper.core.extract.readEntireFileToByteArray
import org.jetbrains.amper.core.extract.writeFully
import org.jetbrains.amper.core.hashing.sha256String
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs.Change.ChangeType
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.setMapAttribute
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
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
    /**
     * The directory where the cache state should be stored.
     */
    private val stateRoot: Path,
    /**
     * Represents the identity of the code being executed. It should change when the cached logic changes.
     * The cache will be discarded and rebuilt if it was stored using a different [codeVersion].
     *
     * One suitable option for this [codeVersion] is to use the hash of the currently running code.
     * The [computeClassPathHash] helper provides a way to create a hash of all jars on the classpath.
     *
     * Note: this doesn't denote a namespace. If multiple instances of [ExecuteOnChangedInputs] are used with the same
     * [stateRoot] but different [codeVersion]s, they will overwrite a single state, not access independent states.
     * Use different [stateRoot]s if you need independent states.
     */
    private val codeVersion: String,
) {
    // increment this counter if you change the state file format
    private val stateFileFormatVersion = 3

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
     * Note: output _properties_ from previous executions don't affect caching because their values only exist in the
     * state file itself, so we have nothing to compare that to.
     * In short, there is no concept of "change" that we could track (unlike output files).
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
    ): IncrementalExecutionResult = spanBuilder("inc $id")
        .setMapAttribute("configuration", configuration)
        .setListAttribute("inputs", inputs.map { it.pathString }.sorted())
        .use { span ->

            stateRoot.createDirectories()

            val sanitizedId = id.replace(Regex("[^a-zA-Z0-9]"), "_")
            // hash includes stateFileFormatVersion to automatically use a different file if the file format was changed
            val hash = "$id\nstate format version: $stateFileFormatVersion".sha256String().take(10)
            val stateFile = stateRoot.resolve("$sanitizedId-$hash")

            // Prevent parallel execution of this 'id' from this or other processes,
            // tracked by a lock on state file
            withLock(id, stateFile) { stateFileChannel ->
                val (cachedState, cacheCheckTime) = measureTimedValue {
                    getCachedState(stateFile, stateFileChannel, configuration, inputs)
                }
                if (cachedState != null && !cachedState.outdated) {
                    logger.debug("[inc] up-to-date according to state file at '{}' in {}", stateFile, cacheCheckTime)
                    logger.debug("[inc] '$id' is up-to-date")
                    span.setAttribute("status", "up-to-date")
                    val existingResult = ExecutionResult(cachedState.state.outputs.map { Path(it) }, cachedState.state.outputProperties)
                    addResultToSpan(span, existingResult)
                    return@withLock IncrementalExecutionResult(existingResult, listOf())
                } else {
                    span.setAttribute("status", "requires-building")
                    logger.debug("[inc] building '$id'")
                }

                val (result, buildTime) = measureTimedValue { block() }

                addResultToSpan(span, result)

                logger.debug("[inc] finished '$id' in $buildTime")

                writeStateFile(stateFileChannel, configuration, inputs, result)

                // TODO remove this check later or hide under debug/assert mode
                ensureStateFileIsConsistent(stateFile, stateFileChannel, configuration, inputs, result)

                val oldState = cachedState?.state?.outputsState ?: mapOf()
                val newState = getPathListState(result.outputs, failOnMissing = false)

                val changes = oldState compare newState

                return@withLock IncrementalExecutionResult(result, changes).also {
                    logger.debug("[inc] '$id' changes: {}", changes.joinToString { "'${it.path}' ${it.type}" })
                }
            }
        }

    private fun addResultToSpan(span: Span, result: ExecutionResult) {
        span.setListAttribute("outputs", result.outputs.map { it.pathString }.sorted())
        span.setMapAttribute("output-properties", result.outputProperties)
    }

    private fun writeStateFile(stateFileChannel: FileChannel, configuration: Map<String, String>, inputs: List<Path>, result: ExecutionResult) {
        val state = State(
            codeVersion = codeVersion,
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
    data class State(
        val codeVersion: String,
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
            val r = getCachedState(stateFile, stateFileChannel, configuration, inputs)
                ?.state
                ?.let { ExecutionResult(it.outputs.map { Path(it) }, it.outputProperties) }
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

    data class CachedState(val state: State, val outdated: Boolean)

    // TODO Probably rewrite to JSON? or a binary format?
    private fun getCachedState(stateFile: Path, stateFileChannel: FileChannel, configuration: Map<String, String>, inputs: List<Path>): CachedState? {
        if (stateFileChannel.size() <= 0) {
            logger.debug("[inc] state file is missing or empty at '{}' -> rebuilding", stateFile)
            return null
        }

        val stateText = try {
            stateFileChannel.position(0)
            stateFileChannel.readEntireFileToByteArray().decodeToString()
        } catch (t: Throwable) {
            logger.warn("[inc] Unable to read state file '$stateFile' -> rebuilding", t)
            return null
        }

        if (stateText.isBlank()) {
            logger.warn("[inc] Previous state file '$stateFile' is empty -> rebuilding")
            return null
        }

        val state = try {
            jsonSerializer.decodeFromString<State>(stateText)
        } catch (t: Throwable) {
            logger.warn("[inc] Unable to deserialize state file '$stateFile' -> rebuilding", t)
            return null
        }

        if (state.codeVersion != codeVersion) {
            logger.debug(
                "[inc] State file '$stateFile' was generated with potentially different logic -> rebuilding\n" +
                        "old: ${state.codeVersion}\n" +
                        "current: $codeVersion"
            )
            return CachedState(state = state, outdated = true)
        }

        if (state.configuration != configuration) {
            // TODO better reporting what was exactly changed
            logger.debug(
                "[inc] state file has a wrong configuration at '$stateFile' -> rebuilding\n" +
                        "  old: ${state.configuration}\n" +
                        "  new: $configuration"
            )
            return CachedState(state = state, outdated = true)
        }

        val inputPaths = inputs.map { it.pathString }.toSet()
        if (state.inputs != inputPaths) {
            logger.debug(
                "[inc] state file has a wrong inputs list at '$stateFile' -> rebuilding\n" +
                        "  old: ${state.inputs.sorted()}\n" +
                        "  new: ${inputPaths.sorted()}"
            )
            return CachedState(state = state, outdated = true)
        }

        val currentInputsState = getPathListState(inputs, failOnMissing = false)
        if (state.inputsState != currentInputsState) {
            logger.debug(
                "[inc] state file has a wrong inputs at '$stateFile' -> rebuilding\n" +
                        "  old: ${state.inputsState}\n" +
                        "  new: $currentInputsState"
            )
            return CachedState(state = state, outdated = true)
        }

        val outputsList = state.outputs.map { Path(it) }
        val currentOutputsState = getPathListState(outputsList, failOnMissing = false)
        if (state.outputsState != currentOutputsState) {
            logger.debug(
                "[inc] state file has a wrong outputs at '$stateFile' -> rebuilding\n" +
                        "  old: ${state.outputsState}\n" +
                        "  new: $currentOutputsState"
            )
            return CachedState(state = state, outdated = true)
        }

        return CachedState(state = state, outdated = false)
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
                        onPreVisitDirectory { subdir, _ ->
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

                        onPostVisitDirectory { _, exc ->
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

    open class ExecutionResult(val outputs: List<Path>, val outputProperties: Map<String, String> = emptyMap())
    
    data class IncrementalExecutionResult(private val executionResult: ExecutionResult, val changes: List<Change>): ExecutionResult(executionResult.outputs, executionResult.outputProperties)
    
    data class Change(val path: Path, val type: ChangeType) {
        enum class ChangeType { CREATED, MODIFIED, DELETED }
    }
    
    private infix fun Map<String, String>.compare(state: Map<String, String>): List<Change> = buildList {
        for (key in keys union state.keys) {
            when {
                key !in this@compare -> add(Change(Path(key), ChangeType.CREATED))
                key !in state -> add(Change(Path(key), ChangeType.DELETED))
                this@compare[key] != state[key] -> add(Change(Path(key), ChangeType.MODIFIED))
            }
        }    
    }

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

/**
 * Executes the given [block] and returns the output file paths, or immediately returns an existing result from the
 * incremental cache.
 *
 * This is exactly equivalent to [ExecuteOnChangedInputs.execute], but without the need to wrap and unwrap the results
 * for cases where we don't need output properties.
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
suspend fun ExecuteOnChangedInputs.executeForFiles(
    id: String,
    configuration: Map<String, String>,
    inputs: List<Path>,
    block: suspend () -> List<Path>,
): List<Path> = execute(id, configuration, inputs) { ExecuteOnChangedInputs.ExecutionResult(block()) }.outputs

private fun String.ensureEndsWith(suffix: String) =
    if (!endsWith(suffix)) (this + suffix) else this
