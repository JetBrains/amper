/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.junit.jupiter.api.TestInfo
import java.security.MessageDigest

fun TestInfo.nameToDependency(): String = testMethod.get().name.replace('_', '.').replace(' ', ':')

fun TestInfo.nameToGoldenFile(): String = testMethod.get().name.replace("_", ".").replace(" ", "_")

@OptIn(ExperimentalStdlibApi::class)
fun computeHash(algorithm: String, bytes: ByteArray): String =
    MessageDigest.getInstance(algorithm).digest(bytes).toHexString()

internal fun String.toMavenNode(context: Context): MavenDependencyNode {
    val (group, module, version) = split(":")
    val mavenDependency = context.createOrReuseDependency(group, module, version, isBom = false)
    return context.getOrCreateNode(mavenDependency, null)
}
