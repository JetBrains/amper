/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.test.TestFilter

/**
 * Settings that are passed from the command line to user-visible processes that Amper runs, such as tests or the
 * user's applications.
 */
data class CommonRunSettings(
    /**
     * The arguments to use when running the user's JVM or native application.
     */
    val programArgs: List<String> = emptyList(),
    /**
     * Filters to select or exclude tests.
     */
    val testFilters: List<TestFilter> = emptyList(),
    /**
     * The JVM args passed by the user.
     *
     * They should be used in JVMs that we launch on behalf of the user and that are exposed to the user.
     * Namely, JVMs used to run the user's JVM application or to run some JVM tests.
     */
    val userJvmArgs: List<String> = emptyList(),
    /**
     * User provided platform-specific string that identifies the device (physical or emulator) to run on.
     */
    val deviceId: String? = null,
)
