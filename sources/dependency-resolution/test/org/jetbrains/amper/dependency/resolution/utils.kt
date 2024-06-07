/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.jetbrains.amper.concurrency.toHex
import org.junit.jupiter.api.TestInfo
import java.security.MessageDigest

fun TestInfo.nameToDependency(): String = testMethod.get().name.replace('_', '.').replace(' ', ':')

fun computeHash(algorithm: String, bytes: ByteArray): String {
    val messageDigest = MessageDigest.getInstance(algorithm)
    messageDigest.update(bytes, 0, bytes.size)
    val hash = messageDigest.digest()
    return hash.toHex()
}

internal fun String.toMavenNode(context: Context): MavenDependencyNode {
    val (group, module, version) = split(":")
    return MavenDependencyNode(context, group, module, version)
}
