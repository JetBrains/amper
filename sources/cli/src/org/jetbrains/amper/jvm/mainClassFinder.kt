/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jvm

import org.jetbrains.amper.frontend.Fragment
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk

/**
 * Finds the fully qualified name of the JVM main class for these fragments.
 * If it's not explicitly defined by the user in the settings, the sources are inspected to find the main class based
 * on the Amper naming convention.
 */
internal fun List<Fragment>.findEffectiveJvmMainClass(): String? {
    // TODO replace with unanimous setting getter
    val explicitMainClass = firstNotNullOfOrNull { it.settings.jvm.mainClass }
    if (explicitMainClass != null) {
        return explicitMainClass
    }

    // TODO what if several fragments have main.kt?
    return firstNotNullOfOrNull { it.findConventionalEntryPoint() }
}
 
/**
 * Finds the fist source file named `main.kt` (ignoring case), if any, and returns the corresponding fqn.
 * This is the convention defined in Amper documentation.
 */
@OptIn(ExperimentalPathApi::class)
private fun Fragment.findConventionalEntryPoint(): String? {
    if (!src.isDirectory()) {
        return null
    }

    val firstMainKtFile = src.walk(PathWalkOption.BREADTH_FIRST)
        .firstOrNull { it.name.equals("main.kt", ignoreCase = true) }

    if (firstMainKtFile == null) {
        return null
    }

    val pkg = firstMainKtFile.readPackageName()
    val prefix = if (pkg != null) "${pkg}." else ""

    return "${prefix}MainKt"
}

private val packageRegex = "^package\\s+([\\w.]+)".toRegex(RegexOption.MULTILINE)

private fun Path.readPackageName(): String? = packageRegex.find(readText())?.groupValues?.get(1)?.trim()
