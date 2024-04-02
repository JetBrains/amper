/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test.extensions

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext

class ErrorCollectorExtension : Extension, BeforeEachCallback, AfterEachCallback {
    private val collected = mutableListOf<Throwable>()

    fun addException(ex: Throwable) = synchronized(collected) {
        collected.add(ex)
    }

    override fun beforeEach(context: ExtensionContext?) {
        synchronized(collected) {
            collected.clear()
        }
    }

    override fun afterEach(context: ExtensionContext?) {
        val exceptions = synchronized(collected) {
            collected.toList().also { collected.clear() }
        }

        if (exceptions.isNotEmpty()) {
            throw exceptions.first().also { parent ->
                for (nested in exceptions.drop(1)) {
                    parent.addSuppressed(nested)
                }
            }
        }
    }
}