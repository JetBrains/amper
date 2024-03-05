/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.concurrency

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

// https://gist.github.com/elizarov/9a48b9709ffd508909d34fab6786acfe
// https://github.com/Kotlin/kotlinx.coroutines/issues/1686#issuecomment-825547551

// from community/platform/diagnostic/telemetry.exporters/src/ReentrantMutex.kt

suspend fun <T> Mutex.withReentrantLock(block: suspend () -> T): T {
    val key = ReentrantMutexContextKey(this)
    // call block directly when this mutex is already locked in the context
    if (coroutineContext[key] != null) {
        return block()
    }

    // otherwise, add it to the context and lock the mutex
    return withContext(ReentrantMutexContextElement(key)) {
        withLock { block() }
    }
}

private class ReentrantMutexContextElement(override val key: ReentrantMutexContextKey) : CoroutineContext.Element

private data class ReentrantMutexContextKey(@JvmField val mutex: Mutex) : CoroutineContext.Key<ReentrantMutexContextElement>
