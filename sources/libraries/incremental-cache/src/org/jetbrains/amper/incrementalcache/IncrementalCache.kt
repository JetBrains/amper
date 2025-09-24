/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("BlockingMethodInNonBlockingContext", "LoggingStringTemplateAsArgument")

package org.jetbrains.amper.incrementalcache

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.amper.concurrency.withReentrantLock
import org.jetbrains.amper.filechannels.readText
import org.jetbrains.amper.incrementalcache.IncrementalCache.Change.ChangeType
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.setMapAttribute
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString
import kotlin.time.measureTimedValue

class IncrementalCache(
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
     * Note: this doesn't denote a namespace. If multiple instances of [IncrementalCache] are used with the same
     * [stateRoot] but different [codeVersion]s, they will overwrite a single state, not access independent states.
     * Use different [stateRoot]s if you need independent states.
     */
    private val codeVersion: String,
    /**
     * The telemetry instance to use for tracing. If not provided, a no-op instance will be used.
     */
    private val openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
) {
    private val tracer: Tracer
        get() = openTelemetry.getTracer("org.jetbrains.amper.incrementalcache")

    /**
     * Executes the given [block] or returns an existing result from the incremental cache for the given [key].
     *
     * ### Caching
     *
     * The previous result for the same [key] is immediately returned without executing [block] if all the following
     * conditions are met:
     *  * the [inputValues] map has not changed
     *  * the given set of [inputFiles] paths has not changed
     *  * the [inputFiles] themselves have not changed (in terms of size, modification time, and permissions)
     *  * the output files from the latest execution have not changed (in terms of size, modification time,
     *    and permissions)
     *  * the version of the code that produced the cached result is the same as the current version
     *
     * Note: output _values_ from previous executions don't affect caching because the values only exist in the state
     * file itself, so we have nothing to compare that to.
     * In short, there is no concept of "change" that we could track (unlike output files).
     *
     * ### Concurrency
     *
     * The given [block] is always executed under double-locking based on the given [key], which means that 2 calls with
     * the same [key] cannot be executed at the same time by multiple threads or multiple processes.
     * If one call needs to re-run [block] because the cache is invalid, subsequent calls with the same ID will suspend
     * until the first call completes and then resume and use the cache immediately (if possible).
     */
    suspend fun execute(
        key: String,
        inputValues: Map<String, String>,
        inputFiles: List<Path>,
        forceRecalculation: Boolean = false,
        block: suspend () -> ExecutionResult,
    ): IncrementalExecutionResult = tracer.spanBuilder("inc $key")
        .setMapAttribute("inputValues", inputValues)
        .setListAttribute("inputFiles", inputFiles.map { it.pathString }.sorted())
        .use { span ->

            stateRoot.createDirectories()

            val sanitizedKey = key.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
            // hash includes stateFileFormatVersion to automatically use a different file if the file format was changed
            val hash = shortHash("$key\nstate format version: ${State.formatVersion}")
            val stateFile = stateRoot.resolve("$sanitizedKey-$hash")

            // Prevent parallel execution of this 'id' from this or other processes,
            // tracked by a lock on the state file
            withLock(key, stateFile) { stateFileChannel ->
                val (cachedState, cacheCheckTime) = measureTimedValue {
                    getCachedState(stateFile, stateFileChannel, inputValues, inputFiles)
                }
                if (cachedState != null && !cachedState.outdated && !forceRecalculation) {
                    logger.debug("[inc] up-to-date according to state file at '{}' in {}", stateFile, cacheCheckTime)
                    logger.debug("[inc] '$key' is up-to-date")
                    span.setAttribute("status", "up-to-date")
                    val existingResult =
                        ExecutionResult(cachedState.state.outputFiles.map { Path(it) }, cachedState.state.outputValues)
                    addResultToSpan(span, existingResult)
                    return@withLock IncrementalExecutionResult(existingResult, listOf())
                } else {
                    span.setAttribute("status", "requires-building")
                    span.setAttribute("forceRecalculation", "$forceRecalculation")
                    logger.debug("[inc] building '$key'")
                }

                val (result, buildTime) = measureTimedValue { block() }

                addResultToSpan(span, result)

                logger.debug("[inc] finished '$key' in $buildTime")

                val state = recordState(inputValues, inputFiles, result)
                stateFileChannel.writeState(state)

                // TODO remove this check later or hide under debug/assert mode
                ensureStateFileIsConsistent(stateFile, stateFileChannel, inputValues, inputFiles, result)

                val oldState = cachedState?.state?.outputFilesState ?: mapOf()
                val newState = readFileStates(
                    paths = result.outputFiles,
                    excludedFiles = result.excludedOutputFiles,
                    failOnMissing = false,
                )

                val changes = oldState compare newState

                return@withLock IncrementalExecutionResult(result, changes).also {
                    logger.debug("[inc] '$key' changes: {}", changes.joinToString { "'${it.path}' ${it.type}" })
                }
            }
        }

    @OptIn(ExperimentalStdlibApi::class)
    private fun shortHash(key: String): String = MessageDigest.getInstance("SHA-256")
        .digest(key.encodeToByteArray())
        .toHexString()
        .take(10)

    private fun addResultToSpan(span: Span, result: ExecutionResult) {
        span.setListAttribute("output-files", result.outputFiles.map { it.pathString }.sorted())
        span.setMapAttribute("output-values", result.outputValues)
    }

    private fun recordState(
        inputValues: Map<String, String>,
        inputFiles: List<Path>,
        result: ExecutionResult,
    ): State = State(
        codeVersion = codeVersion,
        inputValues = inputValues,
        inputFiles = inputFiles.map { it.pathString }.toSet(),
        inputFilesState = readFileStates(paths = inputFiles, excludedFiles = emptySet(), failOnMissing = false),
        outputValues = result.outputValues,
        outputFiles = result.outputFiles.map { it.pathString }.toSet(),
        outputFilesState = readFileStates(
            result.outputFiles,
            excludedFiles = result.excludedOutputFiles,
            failOnMissing = true
        ),
        excludedOutputFiles = result.excludedOutputFiles.map { it.pathString }.toSet(),
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
                ?.let { ExecutionResult(it.outputFiles.map { Path(it) }, it.outputValues) }
                ?: run {
                    stateFileChannel.position(0)
                    val stateText = stateFileChannel.readText()
                    error("Not up-to-date after successfully writing a state file: $stateFile\n" +
                         "--- BEGIN $stateFile\n" +
                         stateText.ensureEndsWith("\n") +
                         "--- END $stateFile")
                }

            if (r.outputFiles != result.outputFiles) {
                error(
                    "Output files list mismatch: $stateFile:\n" +
                            "1: ${r.outputFiles}\n" +
                            "2: ${result.outputFiles}"
                )
            }

            if (r.outputValues != result.outputValues) {
                error(
                    "Output values mismatch: $stateFile:\n" +
                            "1: ${r.outputValues.map { "${it.key}=${it.value}" }.sorted()}\n" +
                            "2: ${result.outputValues.map { "${it.key}=${it.value}" }.sorted()}"
                )
            }
        } catch (t: Throwable) {
            stateFile.deleteIfExists()
            throw t
        }
    }

    private data class CachedState(val state: State, val outdated: Boolean)

    private fun getCachedState(
        stateFile: Path,
        stateFileChannel: FileChannel,
        inputValues: Map<String, String>,
        inputFiles: List<Path>,
    ): CachedState? {
        val state = stateFileChannel.readState(pathForLogs = stateFile) ?: return null

        if (state.codeVersion != codeVersion) {
            logger.debug(
                "[inc] State file '$stateFile' was generated with potentially different logic -> rebuilding\n" +
                        "old: ${state.codeVersion}\n" +
                        "current: $codeVersion"
            )
            return CachedState(state = state, outdated = true)
        }

        if (state.inputValues != inputValues) {
            // TODO better reporting what was exactly changed
            logger.debug(
                "[inc] Input values don't match recorded state in $stateFile -> rebuilding\n" +
                        "  old: ${state.inputValues}\n" +
                        "  new: $inputValues"
            )
            return CachedState(state = state, outdated = true)
        }

        val inputPaths = inputFiles.map { it.pathString }.toSet()
        if (state.inputFiles != inputPaths) {
            logger.debug(
                "[inc] Input files list doesn't match recorded state in '$stateFile' -> rebuilding\n" +
                        "  old: ${state.inputFiles.sorted()}\n" +
                        "  new: ${inputPaths.sorted()}"
            )
            return CachedState(state = state, outdated = true)
        }

        val currentInputsState = readFileStates(inputFiles, excludedFiles = emptySet(), failOnMissing = false)
        if (state.inputFilesState != currentInputsState) {
            logger.debug(
                "[inc] Input files don't match recorded state in '$stateFile' -> rebuilding\n" +
                        "  old: ${state.inputFilesState}\n" +
                        "  new: $currentInputsState"
            )
            return CachedState(state = state, outdated = true)
        }

        val outputsList = state.outputFiles.map { Path(it) }
        val excludedOutputs = state.excludedOutputFiles.mapTo(mutableSetOf()) { Path(it) }
        val currentOutputsState = readFileStates(outputsList, excludedFiles = excludedOutputs, failOnMissing = false)
        if (state.outputFilesState != currentOutputsState) {
            logger.debug(
                "[inc] Output files don't match recorded state in '$stateFile' -> rebuilding\n" +
                        "  old: ${state.outputFilesState}\n" +
                        "  new: $currentOutputsState"
            )
            return CachedState(state = state, outdated = true)
        }

        return CachedState(state = state, outdated = false)
    }

    open class ExecutionResult(
        /**
         * The output files and directories created by the computation.
         *
         * The state of these files is recorded and persisted on disk. The next time the computation is run, the new
         * state of the files is compared to the recorded state, and the computation is re-run if anything changed.
         */
        val outputFiles: List<Path>,
        /**
         * The key-value pairs produced by the computation.
         */
        val outputValues: Map<String, String> = emptyMap(),
        /**
         * The files to ignore when comparing the state of the output files.
         * Changes in these files do not invalidate the cache state.
         */
        val excludedOutputFiles: Set<Path> = emptySet(),
    )
    
    data class IncrementalExecutionResult(
        private val executionResult: ExecutionResult,
        val changes: List<Change>,
    ): ExecutionResult(executionResult.outputFiles, executionResult.outputValues)
    
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
        private val logger = LoggerFactory.getLogger(IncrementalCache::class.java)

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
 * incremental cache for the given [key].
 *
 * This is exactly equivalent to [IncrementalCache.execute], but without the need to wrap and unwrap the results
 * for cases where we just need output files and don't need output values.
 *
 * ### Caching
 *
 * The previous result for the same [key] is immediately returned without executing [block] if all the following
 * conditions are met:
 *  * the [inputValues] map has not changed
 *  * the given set of [inputFiles] paths has not changed
 *  * the [inputFiles] themselves have not changed (in terms of size, modification time, and permissions)
 *  * the output files from the latest execution have not changed (in terms of size, modification time,
 *    and permissions)
 *  * the version of the code that produced the cached result is the same as the current version
 *
 * ### Concurrency
 *
 * The given [block] is always executed under double-locking based on the given [key], which means that 2 calls with
 * the same [key] cannot be executed at the same time by multiple threads or multiple processes.
 * If one call needs to re-run [block] because the cache is invalid, subsequent calls with the same ID will suspend
 * until the first call completes and then resume and use the cache immediately (if possible).
 */
suspend fun IncrementalCache.executeForFiles(
    key: String,
    inputValues: Map<String, String>,
    inputFiles: List<Path>,
    block: suspend () -> List<Path>,
): List<Path> = execute(key, inputValues, inputFiles) { IncrementalCache.ExecutionResult(block()) }.outputFiles

private fun String.ensureEndsWith(suffix: String) =
    if (!endsWith(suffix)) this + suffix else this
