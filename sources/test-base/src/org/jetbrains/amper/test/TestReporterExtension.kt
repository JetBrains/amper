/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.atomic.AtomicReference

class TestReporterExtension: Extension, BeforeEachCallback, AfterEachCallback, TestReporter {
    private val currentContext = AtomicReference<ExtensionContext>()

    override fun publishEntry(map: Map<String, String>) {
        currentContext.get()!!.publishReportEntry(map)
    }

    override fun beforeEach(context: ExtensionContext) {
        val oldContext = currentContext.getAndSet(context)
        check(oldContext == null) {
            "TestReporterExtension can be used only once per test"
        }
    }

    override fun afterEach(context: ExtensionContext) {
        val oldContext = currentContext.getAndSet(null)
        check(oldContext === context) {
            "TestReporterExtension can be used only once per test"
        }
    }
}
