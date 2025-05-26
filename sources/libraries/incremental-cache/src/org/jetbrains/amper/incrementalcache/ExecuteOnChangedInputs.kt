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
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs.Change.ChangeType
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
    /**
     * The telemetry instance to use for tracing. If not provided, a no-op instance will be used.
     */
    private val openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
) {
    private val tracer: Tracer
        get() = openTelemetry.getTracer("org.jetbrains.amper.incrementalcache")

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
     *  * the version of the code that produced the cached result is the same as the current version
     *
     * Note: output _properties_ from previous executions don't affect caching because their values only exist in the
     * state file itself, so we have nothing to compare that to.
     * In short, there is no concept of "change" that we could track (unlike output files).
     *
     * ### Concurrency
     *
     * The given [block] is always executed under double-locking based on the given [id], which means that 2 calls with
     * the same [id] cannot be executed at the same time by multiple threads or multiple processes.
     * If one call needs to re-run [block] because the cache is invalid, subsequent calls with the same ID will suspend
     * until the first call completes and then resume and use the cache immediately (if possible).
     */
    suspend fun execute(
        id: String,
        configuration: Map<String, String>,
        inputs: List<Path>,
        block: suspend () -> ExecutionResult
    ): IncrementalExecutionResult = tracer.spanBuilder("inc $id")
        .setMapAttribute("configuration", configuration)
        .setListAttribute("inputs", inputs.map { it.pathString }.sorted())
        .use { span ->

            stateRoot.createDirectories()

            val sanitizedId = id.replace(Regex("[^a-zA-Z0-9]"), "_")
            // hash includes stateFileFormatVersion to automatically use a different file if the file format was changed
            val hash = shortHash("$id\nstate format version: ${State.formatVersion}")
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
                val newState = readFileStates(
                    paths = result.outputs,
                    excludedFiles = result.excludedOutputs,
                    failOnMissing = false,
                )

                val changes = oldState compare newState

                return@withLock IncrementalExecutionResult(result, changes).also {
                    logger.debug("[inc] '$id' changes: {}", changes.joinToString { "'${it.path}' ${it.type}" })
                }
            }
        }

    @OptIn(ExperimentalStdlibApi::class)
    private fun shortHash(key: String): String = MessageDigest.getInstance("SHA-256")
        .digest(key.encodeToByteArray())
        .toHexString()
        .take(10)

    private fun addResultToSpan(span: Span, result: ExecutionResult) {
        span.setListAttribute("outputs", result.outputs.map { it.pathString }.sorted())
        span.setMapAttribute("output-properties", result.outputProperties)
    }

    private fun writeStateFile(stateFileChannel: FileChannel, configuration: Map<String, String>, inputs: List<Path>, result: ExecutionResult) {
        val state = State(
            codeVersion = codeVersion,
            configuration = configuration,
            inputs = inputs.map { it.pathString }.toSet(),
            inputsState = readFileStates(paths = inputs, excludedFiles = emptySet(), failOnMissing = false),
            outputs = result.outputs.map { it.pathString }.toSet(),
            excludedOutputs = result.excludedOutputs.map { it.pathString }.toSet(),
            outputsState = readFileStates(result.outputs, excludedFiles = result.excludedOutputs, failOnMissing = true),
            outputProperties = result.outputProperties,
        )

        stateFileChannel.writeState(state)
    }

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
                    val stateText = stateFileChannel.readText()
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

    private data class CachedState(val state: State, val outdated: Boolean)

    private fun getCachedState(stateFile: Path, stateFileChannel: FileChannel, configuration: Map<String, String>, inputs: List<Path>): CachedState? {
        val state = stateFileChannel.readState(pathForLogs = stateFile) ?: return null

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

        val currentInputsState = readFileStates(inputs, excludedFiles = emptySet(), failOnMissing = false)
        if (state.inputsState != currentInputsState) {
            logger.debug(
                "[inc] state file has a wrong inputs at '$stateFile' -> rebuilding\n" +
                        "  old: ${state.inputsState}\n" +
                        "  new: $currentInputsState"
            )
            return CachedState(state = state, outdated = true)
        }

        val outputsList = state.outputs.map { Path(it) }
        val excludedOutputs = state.excludedOutputs.mapTo(mutableSetOf()) { Path(it) }
        val currentOutputsState = readFileStates(outputsList, excludedFiles = excludedOutputs, failOnMissing = false)
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

    open class ExecutionResult(
        /**
         * The output files and directories created by the computation.
         *
         * The state of these files is recorded and persisted on disk. The next time the computation is run, the new
         * state of the files is compared to the recorded state, and the computation is re-run if anything changed.
         */
        val outputs: List<Path>,
        /**
         * The key-value pairs produced by the computation.
         */
        val outputProperties: Map<String, String> = emptyMap(),
        /**
         * The files to ignore when comparing the state of the output files.
         * Changes in these files do not invalidate the cache state.
         */
        val excludedOutputs: Set<Path> = emptySet(),
    )
    
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
 *  * the version of the code that produced the cached result is the same as the current version
 *
 * ### Concurrency
 *
 * The given [block] is always executed under double-locking based on the given [id], which means that 2 calls with
 * the same [id] cannot be executed at the same time by multiple threads or multiple processes.
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
