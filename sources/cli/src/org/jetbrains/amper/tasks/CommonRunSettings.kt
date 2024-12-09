/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

data class CommonRunSettings(
    val programArgs: List<String> = emptyList(),
    /**
     * The JVM args passed by the user.
     *
     * They should be used in JVMs that we launch on behalf of the user and that are exposed to the user.
     * Namely, JVMs used to run the user's JVM application or to run some JVM tests.
     */
    val userJvmArgs: List<String> = emptyList(),
)
