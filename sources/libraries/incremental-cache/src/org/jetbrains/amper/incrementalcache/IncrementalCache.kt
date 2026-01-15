/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("BlockingMethodInNonBlockingContext", "LoggingStringTemplateAsArgument")

package org.jetbrains.amper.incrementalcache

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.amper.concurrency.withReentrantLock
import org.jetbrains.amper.incrementalcache.DynamicInputsTracker.Companion.withDynamicInputsTracker
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
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.time.Clock
import kotlin.time.Instant

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
    internal val tracer: Tracer
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
        /**
         * Calculation of cache entry.
         * If calculation depends on environment parameters such as
         * system properties, environment variables, or the existence of local paths
         * which are not known in advance (and thus could not be added to input values),
         * such environment parameters should be resolved with help of the dynamic inputs tracker provided by [getDynamicInputs].
         * This way access to environment parameters is tracked,
         * and cache entry will be recalculated automatically on subsequent access
         * if any of these environment parameters changes.
         */
        block: suspend () -> ExecutionResult,
    ): IncrementalExecutionResult = tracer.spanBuilder("inc: run: $key")
        .setMapAttribute("inputValues", inputValues)
        .setListAttribute("inputFiles", inputFiles.map { it.pathString }.sorted())
        .use { span ->

            stateRoot.createDirectories()

            val stateFile = stateFileFor(key)

            // Prevent parallel execution of this 'id' from this or other processes,
            // tracked by a lock on the state file
            withLock(key, stateFile) { stateFileChannel ->
                val cachedState = tracer.spanBuilder("inc: get-cached-state").use {
                    getCachedState(stateFile, stateFileChannel, inputValues, inputFiles)
                }

                if (cachedState != null && !cachedState.outdated && !forceRecalculation) {
                    logger.debug("[inc] '$key' is up-to-date according to state file at '{}'", stateFile)
                    span.setAttribute("status", "up-to-date")
                    val existingResult =
                        ExecutionResult(
                            outputFiles = cachedState.state.outputFiles.map { Path(it) },
                            outputValues = cachedState.state.outputValues,
                            expirationTime = cachedState.state.expirationTime
                        )
                    // Adding dynamic inputs used for calculating this cache entry to the dynamic inputs of the upstream cache (if any).
                    DynamicInputsTracker.getCurrentTracker()?.addFrom(cachedState.state.dynamicInputs)

                    span.addResult(existingResult, cachedState.state.dynamicInputs)
                    return@withLock IncrementalExecutionResult(existingResult, listOf())
                } else {
                    span.setAttribute("status", "requires-building")
                    span.setAttribute("forceRecalculation", "$forceRecalculation")
                    logger.debug("[inc] building '$key'")
                }

                // dynamic inputs tracker for registering environments parameters used for this cache entry calculation
                val tracker = DynamicInputsTracker()

                val result = tracer.spanBuilder("inc: execute").use {
                    withDynamicInputsTracker(tracker) {
                        block()
                    }
                }
                val dynamicInputsState = tracker.toState().also {
                    // Adding dynamic inputs used for calculating this cache entry to the dynamic inputs of the upstream cache (if any).
                    DynamicInputsTracker.getCurrentTracker()?.addFrom(it)
                }

                span.addResult(result, dynamicInputsState)

                tracer.spanBuilder("inc: write-state").use {
                    val state = recordState(inputValues, inputFiles, dynamicInputsState, result)
                    stateFileChannel.writeState(state)
                }

                val oldOutputFilesState = cachedState?.state?.outputFilesState ?: mapOf()
                val newOutputFilesState = tracer.spanBuilder("inc: read-new-file-states").use {
                    readFileStates(
                        paths = result.outputFiles,
                        excludedFiles = result.excludedOutputFiles,
                        failOnMissing = false,
                    )
                }
                val outputFilesChanges = oldOutputFilesState compare newOutputFilesState

                val dynamicInputsChanges = cachedState?.state?.dynamicInputs?.changes() ?: emptyList()

                val changes = outputFilesChanges + dynamicInputsChanges

                return@withLock IncrementalExecutionResult(result, changes).also {
                    logger.debug("[inc] '$key' changes: {}", changes.joinToString { "'${it.path}' ${it.type}" })
                }
            }
        }

    /**
     * Returns the [Path] to the increment state file used to track the given [key].
     */
    private fun stateFileFor(key: String): Path {
        val sanitizedKey = key.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_").take(50)
        // hash includes stateFileFormatVersion to automatically use a different file if the file format was changed
        val hash = shortHash("$key\nstate format version: ${State.formatVersion}")
        return stateRoot.resolve("$sanitizedKey-$hash")
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun shortHash(key: String): String = MessageDigest.getInstance("MD5")
        .digest(key.encodeToByteArray())
        .toHexString()
        .take(10)

    private fun Span.addResult(result: ExecutionResult, dynamicInputsState: DynamicInputsState) {
        setListAttribute("output-files", result.outputFiles.map { it.pathString }.sorted())

        // [outputValues] are not added as an attribute since it might be huge (serialized dependency graph)
        // and don't help much in investigating incremental cache behavior

        dynamicInputsState.systemProperties.takeIf { it.isNotEmpty() }
            ?.let { setMapAttribute("dynamic-inputs-system-properties", it) }
        dynamicInputsState.environmentVariables.takeIf { it.isNotEmpty() }
            ?.let { setMapAttribute("dynamic-inputs-environment-variables", it) }
        dynamicInputsState.pathsExistence.takeIf { it.isNotEmpty() }
            ?.let { setMapAttribute("dynamic-inputs-paths-existence", it) }
    }

    private fun recordState(
        inputValues: Map<String, String>,
        inputFiles: List<Path>,
        dynamicInputsState: DynamicInputsState,
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
        dynamicInputs = dynamicInputsState,
        expirationTime = result.expirationTime
    )

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

        if (state.expirationTime != null && state.expirationTime < Clock.System.now()) {
            logger.debug(
                "[inc] State file '$stateFile' contains expiration time date is passed already\n" +
                        "expiration time: ${state.expirationTime}\n"
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

        val currentDynamicInputsState = state.dynamicInputs.calculateCurrentState()

        if (state.dynamicInputs.systemProperties != currentDynamicInputsState.systemProperties) {
            logger.debug(
                "[inc] System properties that affecteted cache calculation don't match recorded state in '$stateFile' -> rebuilding\n" +
                        "  old: ${state.dynamicInputs.systemProperties.toSortedMap()}\n" +
                        "  new: ${currentDynamicInputsState.systemProperties.toSortedMap()}"
            )
            return CachedState(state = state, outdated = true)
        }

        if (state.dynamicInputs.environmentVariables != currentDynamicInputsState.environmentVariables) {
            logger.debug(
                "[inc] Environment variables that affecteted cache calculation don't match recorded state in '$stateFile' -> rebuilding\n" +
                        "  old: ${state.dynamicInputs.environmentVariables.toSortedMap()}\n" +
                        "  new: ${currentDynamicInputsState.environmentVariables.toSortedMap()}"
            )
            return CachedState(state = state, outdated = true)
        }

        if (state.dynamicInputs.pathsExistence != currentDynamicInputsState.pathsExistence) {
            logger.debug(
                "[inc] Existence of files affecteted cache calculation don't match recorded state in '$stateFile' -> rebuilding\n" +
                        "  old: ${state.dynamicInputs.pathsExistence.toSortedMap()}\n" +
                        "  new: ${currentDynamicInputsState.pathsExistence.toSortedMap()}"
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
         * state of the files is compared to the recorded state, and the computation is re-run if anything is changed.
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
        /**
         * Date and time the cache entry is no longer valid after, and should be recalculated
         */
        val expirationTime: Instant? = null
    )
    
    data class IncrementalExecutionResult(
        private val executionResult: ExecutionResult,
        val changes: List<Change>,
    ): ExecutionResult(
        executionResult.outputFiles,
        executionResult.outputValues,
        expirationTime = executionResult.expirationTime
    )
    
    data class Change(val path: Path, val type: ChangeType) {
        enum class ChangeType { CREATED, MODIFIED, DELETED }
    }
    
    private infix fun Map<String, String?>.compare(state: Map<String, String?>): List<Change> = buildList {
        for (key in keys union state.keys) {
            when {
                key !in this@compare -> add(Change(Path(key), ChangeType.CREATED))
                key !in state -> add(Change(Path(key), ChangeType.DELETED))
                this@compare[key] != state[key] -> add(Change(Path(key), ChangeType.MODIFIED))
            }
        }    
    }

    private fun DynamicInputsState.calculateCurrentState() =
        DynamicInputsState(
            systemProperties = systemProperties.map { it.key to System.getProperty(it.key) }.toMap(),
            environmentVariables = environmentVariables.map { it.key to System.getenv(it.key) }.toMap(),
            pathsExistence = pathsExistence.map { it.key to Path(it.key).exists().toString() }.toMap()
        )

    private fun DynamicInputsState.changes(): List<Change> = buildList {
        val currentDynamicInputsState = calculateCurrentState()
        addAll(currentDynamicInputsState.environmentVariables compare environmentVariables)
        addAll(currentDynamicInputsState.systemProperties compare systemProperties)
        addAll(currentDynamicInputsState.pathsExistence compare pathsExistence)
    }

    private fun DynamicInputsTracker.toState() =
        DynamicInputsState(
            systemProperties = systemProperties.toMap(),
            environmentVariables = environmentVariables.toMap(),
            pathsExistence = pathsExistence.map { it.key.pathString to it.value.toString() }.toMap(),
        )

    private fun DynamicInputsTracker.addFrom(dynamicInputs: DynamicInputsState) {
        systemProperties.putAll(dynamicInputs.systemProperties)
        environmentVariables.putAll(dynamicInputs.environmentVariables)
        pathsExistence.putAll(dynamicInputs.pathsExistence.map{ Path(it.key) to it.value.toBoolean() }.toMap())
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
 * If one call needs to re-run [block] because the cache is invalid, later calls with the same ID will suspend
 * until the first call completes and then resume and use the cache immediately (if possible).
 */
suspend inline fun IncrementalCache.executeForFiles(
    key: String,
    inputValues: Map<String, String>,
    inputFiles: List<Path>,
    forceRecalculation: Boolean = false,
    crossinline block: suspend () -> List<Path>,
): List<Path> = execute(
    key = key,
    inputValues = inputValues,
    inputFiles = inputFiles,
    forceRecalculation = forceRecalculation,
) {
    IncrementalCache.ExecutionResult(block())
}.outputFiles
