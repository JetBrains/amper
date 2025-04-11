/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Awaits the termination of this [Process] in a suspending and cancellable way.
 *
 * The given [outputListener] is used to handle lines of output from stdout/stderr while the process is running (for
 * instance to print them to the console).
 *
 * **Important:** the stream collection is performed on the current coroutine context, the dispatcher of which must
 * provide **at least 2 threads** (to perform blocking reads of stdout and stderr in parallel). It is the responsibility
 * of the caller to provide a suitable dispatcher.
 *
 * If this function is cancelled, the cancellation is immediate, but **the process is untouched and can keep running**.
 * The same applies if any exception is thrown (for instance, IO exceptions while reading streams).
 * The caller is responsible for ensuring the process doesn't leak, for instance using [withGuaranteedTermination].
 * The reason for this is that the caller of [ProcessBuilder.start] should be responsible for the life of the process.
 *
 * **Implementation note:** this function always waits for the coroutines reading IO streams to finish before returning,
 * ensuring we don't leak any coroutine nor hold on to extra threads. Non-network IO reads are not cancellable, but the
 * readers are made partially cooperative by allowing cancellation between each line read, so they don't have to keep
 * reading the whole stream if the result is meant to be discarded anyway.
 */
// NOT PUBLIC ON PURPOSE, please check the KDoc - the caller is responsible for too many things
internal suspend fun Process.awaitListening(outputListener: ProcessOutputListener): Int = coroutineScope {
    val pid = pid()
    launch {
        inputStream.consumeLinesBlockingCancellable {
            outputListener.onStdoutLine(it, pid)
        }
    }
    launch {
        errorStream.consumeLinesBlockingCancellable {
            outputListener.onStderrLine(it, pid)
        }
    }

    // This is async: onExit() runs on the ForkJoinPool so it doesn't hold the current thread.
    // This is why we only really need 2 threads in the current dispatcher (to consume stdout and stderr).
    onExit().await().exitValue().also { exitCode ->
        outputListener.onProcessTerminated(exitCode, pid)
    }
}

/**
 * Consumes this input stream by reading the data as UTF-8 text line-by-line, and calls [onEachLine] on each line.
 *
 * **WARNING:** this suspending function blocks the thread despite the suspend keyword!
 *
 * This function complies with coroutine cancellation by checking for cancellation between each line read. However, a
 * single line read blocks the thread and cannot be cancelled until it returns (interruption doesn't work on them).
 */
// We're not using Dispatchers.IO on purpose here, so the caller can decide on the thread pool.
private suspend inline fun InputStream.consumeLinesBlockingCancellable(onEachLine: (String) -> Unit) {
    bufferedReader().useLines { lines ->
        try {
            lines.forEach {
                onEachLine(it)

                // We cooperate with cancellation by checking for it between each line read.
                // Using yield() instead of ensureActive() between reads could hurt performance, and wouldn't solve
                // the problem, because we could still block indefinitely on a read that never comes.
                coroutineContext.ensureActive()
            }
        } catch (e: IOException) {
            // If the process is killed externally on unix systems, the stream is eagerly closed,
            // and reading from the sequence throws an IOException with "Stream closed" message,
            // sometimes capitalized as "Stream Closed", or "Bad file descriptor".
            // This check is brittle, but it's kind of the only way to account for this situation
            // by considering this exception as the end of the process output instead of crashing.
            if (e.message?.lowercase() in setOf("stream closed", "bad file descriptor")) {
                return
            }
            throw e
        }
    }
}

/**
 * Executes the given [cancellableBlock], and waits for the process termination. When this function terminates (normally
 * or exceptionally), the process is guaranteed to be terminated:
 *
 * * If [cancellableBlock] completes successfully, this function simply waits for the termination of this process
 *   **without destroying it**.
 * * If [cancellableBlock] throws an exception, this function **destroys the process** and awaits its termination.
 * * If the current coroutine is cancelled, this function **destroys the process** and awaits its termination.
 *   This is true whether the current coroutine is already cancelled when calling this function, or cancelled during the
 *   execution of [cancellableBlock], or cancelled after the successful completion of [cancellableBlock] (while waiting
 *   for the process to terminate).
 * * If the JVM is terminated gracefully (Ctrl+C / SIGINT), this function **requests the process destruction** but
 *   doesn't wait for its completion (we mustn't block the JVM shutdown).
 *
 * The process destruction is done as described in [killAndAwaitTermination]: first, a normal destruction is requested,
 * and if this process doesn't terminate within the given [gracePeriod], it is then forcibly killed.
 *
 * This function always waits for the process to actually terminate before re-throwing an exception (such as
 * [CancellationException]) to make sure this process doesn't leak. If this process is not killable and hangs, this
 * function also hangs instead of returning and leaking this zombie process.
 */
@OptIn(ExperimentalContracts::class)
internal suspend inline fun <T> Process.withGuaranteedTermination(
    gracePeriod: Duration = 1.seconds,
    cancellableBlock: (Process) -> T,
): T {
    contract {
        callsInPlace(cancellableBlock, kind = InvocationKind.EXACTLY_ONCE)
    }
    withDestructionHook {
        try {
            // jump straight to the catch block if the coroutine is already cancelled
            currentCoroutineContext().ensureActive()

            return cancellableBlock(this).also {
                onExit().await()
            }
        } catch (e: Throwable) { // Intentionally catches both errors AND cancellations
            // Intentionally non-cancellable to avoid leaking a live process while seemingly cancelling this call.
            // For hardcore throwables (ThreadDeath, OOM...), we still attempt to kill the process on a best-efforts basis.
            killAndAwaitTermination(gracePeriod)
            throw e
        }
    }
}

/**
 * Executes the given [block], but also ensures that the process is destroyed in case the JVM is killed (e.g. Ctrl+C)
 * during the execution.
 *
 * A shutdown hook is registered before running [block], and unregistered when the given [block] completes (normally or
 * exceptionally).
 */
@OptIn(ExperimentalContracts::class)
@PublishedApi
internal inline fun <T> Process.withDestructionHook(block: (Process) -> T): T {
    contract {
        callsInPlace(block, kind = InvocationKind.EXACTLY_ONCE)
    }
    // If the JVM is shut down (e.g. via Ctrl+C) while this child process is running, we don't want to leak the process.
    // That said, we only call destroyHierarchy(), which is asynchronous, because we can't afford to wait for the
    // process to actually terminate when the JVM is shutting down (we need to promptly terminate).
    return withShutdownHook(onJvmShutdown = { destroyHierarchy() }) {
        block(this)
    }
}

@OptIn(ExperimentalContracts::class)
@PublishedApi
internal inline fun <T> withShutdownHook(crossinline onJvmShutdown: () -> Unit, block: () -> T): T {
    contract {
        callsInPlace(block, kind = InvocationKind.EXACTLY_ONCE)
    }
    val hookThread = Thread { onJvmShutdown() }
    val runtime = Runtime.getRuntime()
    try {
        runtime.addShutdownHook(hookThread)
        return block()
    } finally {
        try {
            runtime.removeShutdownHook(hookThread)
        } catch (_: IllegalStateException) {
            // IllegalStateException is thrown if the JVM is already shutting down.
            // In this case, we actually don't want to unregister the hook, so it's fine to ignore this exception.
            // There is no direct way to check whether the JVM is shutting down before attempting to remove the hook.
        }
    }
}

/**
 * Kills this process and its descendants (first normally, then forcibly) and waits for their termination in a
 * non-cancellable way. If this process is already not alive, this function doesn't do anything and returns immediately.
 *
 * If this process doesn't die within [gracePeriod] after the normal termination request, it is forcibly destroyed.
 * If this process is not killable and hangs, this function also hangs instead of returning and leaking this zombie process.
 *
 * If this function returns normally, the process is guaranteed to be terminated.
 *
 * This operation is not cancellable in the coroutine sense, but it is interruptible. However, interrupting this call
 * should be avoided, as it would allow the process to potentially stay alive, defeating the purpose of this call.
 *
 * @throws InterruptedException if the current thread is interrupted while waiting for the process to terminate
 */
// TODO maybe turn this into non-blocking AND keep it non-cancellable with withContext(NonCancellable) on the call site
@PublishedApi
internal fun Process.killAndAwaitTermination(gracePeriod: Duration = 1.seconds): Int {
    destroyHierarchy()
    // the destroy operation is asynchronous, we need to give this process a chance to complete gracefully
    val completed = waitFor(gracePeriod.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    if (completed) {
        return exitValue()
    }
    destroyHierarchyForcibly()
    // The forced destroy operation is asynchronous, so we need to wait until this process is actually terminated.
    // This may hang if the process is not killable, but if that's the case, it's better to hang than to return and
    // silently have a live zombie process.
    return waitFor()
}

/**
 * Requests the process and all its descendants to be killed. If the process is not alive, no action is taken.
 * The operating system access controls may prevent the process from being killed.
 *
 * Whether the process represented by this [Process] object is normally terminated or not is implementation-dependent.
 * Forcible process destruction is defined as the immediate termination of the process, whereas normal
 * termination allows the process to shut down cleanly.
 */
@PublishedApi
internal fun Process.destroyHierarchy() {
    descendants().forEach { it.destroy() }
    destroy()
}

/**
 * Kills the process and all descendants forcibly. If the process is not alive, no action is taken.
 *
 * The process represented by this [Process] object is forcibly terminated. Forcible process destruction is defined as
 * the immediate termination of a process, whereas normal termination allows the process to shut down cleanly.
 */
private fun Process.destroyHierarchyForcibly() {
    descendants().forEach { it.destroyForcibly() }
    destroyForcibly()
}
