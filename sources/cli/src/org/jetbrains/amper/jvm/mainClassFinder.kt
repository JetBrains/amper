/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jvm

import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.frontend.Fragment
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.walk

/**
 * Finds the fully qualified name of the JVM main class for these fragments.
 *
 * This function first looks for an explicit main class in the user settings.
 * If not found, the sources are inspected to find the main class based on the Amper naming convention.
 * If even this doesn't yield anything, we throw with a user-readable error explaining the problem.
 */
internal fun List<Fragment>.getEffectiveJvmMainClass(): String {
    require(isNotEmpty()) { "The fragment list is empty, cannot find the main class" }
    val module = first().module
    require(module.type.isApplication()) { "Attempting to get the main class for a non-application module" }

    val effectiveMainClass = findEffectiveJvmMainClass()

    if (effectiveMainClass == null) {
        userReadableError(
            "The JVM main class was not found for application module '${module.userReadableName}' in any of the " +
                    "following source directories:\n${joinToString("\n") { "- ${it.src.pathString}" }}\n" +
                    "Make sure a main.kt file is present in your sources with a valid `main` function, or declare " +
                    "the fully-qualified main class explicitly with `settings.jvm.mainClass` in your module file."
        )
    }
    return effectiveMainClass
}

/**
 * Finds the fully qualified name of the JVM main class for these fragments.
 *
 * This function first looks for an explicit main class in the user settings.
 * If not found, the sources are inspected to find the main class based on the Amper naming convention.
 * If not found either, this function returns null.
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
