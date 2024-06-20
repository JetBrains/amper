/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.frontend.PotatoModule
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

/**
 * Returns the Gradle path of this module, as if it were part of a Gradle project with root [projectRoot].
 */
internal fun PotatoModule.gradlePath(projectRoot: AmperProjectRoot): String {
    val moduleDir = source.moduleDir ?: error("Cannot build Android module without directory")
    val relativeModuleDir = moduleDir.relativeTo(projectRoot.path).normalize().invariantSeparatorsPathString
    return ":" + relativeModuleDir.replace('/', ':')
}
