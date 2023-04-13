/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm.sample.bookshelf.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach


// Credit - https://github.com/JetBrains/kotlinconf-app/blob/master/common/src/mobileMain/kotlin/org/jetbrains/kotlinconf/FlowUtils.kt
// Wrapper to consume Flow based API from Obj-C/Swift
// Alternatively we can use the 'Kotlinx_coroutines_coreFlowCollector' protocol from Swift as demonstrated in https://stackoverflow.com/a/66030092
// however the below wrapper gives us more control and hides the complexity in the shared Kotlin code.
class CFlow<T>(private val origin: Flow<T>) : Flow<T> by origin {
    fun watch(block: (T) -> Unit): Closeable {
        val job = Job()

        onEach {
            block(it)
            // [SwiftUI] Publishing changes from background threads is not allowed;
            // make sure to publish values from the main thread
        }.launchIn(CoroutineScope(Dispatchers.Main + job))

        return object : Closeable {
            override fun close() {
                job.cancel()
            }
        }
    }
}
// Helper extension
internal fun <T> Flow<T>.wrap(): CFlow<T> = CFlow(this)

// Remove when Kotlin's Closeable is supported in K/N https://youtrack.jetbrains.com/issue/KT-31066
// Alternatively use Ktor Closeable which is K/N ready.
interface Closeable {
    fun close()
}
