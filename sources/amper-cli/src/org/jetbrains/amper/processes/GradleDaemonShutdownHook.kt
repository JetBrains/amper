/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import org.gradle.tooling.internal.consumer.ConnectorServices
import kotlin.concurrent.thread

/**
 * Forcibly tells Gradle Tooling API to free resources (shutdown daemons, etc.) at the process end.
 */
object GradleDaemonShutdownHook {
    // TODO: Make this a documented property/cli argument at some point.
    const val NO_DAEMON_ENV = "AMPER_NO_GRADLE_DAEMON"

    private val hookPrimed by lazy {
        Runtime.getRuntime().addShutdownHook(thread(
            start = false,
        ) {
            try {
                ConnectorServices.close()
            } catch (e: RuntimeException) {
                e.printStackTrace()
            }
        })
    }

    fun setupIfNeeded() {
        if (System.getenv(NO_DAEMON_ENV) == "1") {
            hookPrimed
        }
    }
}