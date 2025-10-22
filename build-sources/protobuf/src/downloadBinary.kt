/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.protobuf

import java.io.IOException
import java.net.URI
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.collections.plus
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.outputStream
import kotlin.io.path.setPosixFilePermissions

/**
 * Naive toy artifact downloader.
 */
context(systemInfo: SystemInfo)
fun downloadBinary(
    group: String,
    name: String,
    version: String,
    to: Path,
) {
    val path = buildList {
        addAll(group.splitToSequence('.'))
        add(name)
    }.joinToString("/")

    val url = "https://repo1.maven.org/maven2/$path/$version/$name-$version-" +
            "${systemInfo.os.string}-${systemInfo.arch.string}.exe"
    try {
        URI(url).toURL().openStream().buffered(64 * 1024).use { input ->
            to.outputStream().use {
                input.copyTo(it)
            }
        }
        when (systemInfo.os) {
            SystemInfo.Os.Linux, SystemInfo.Os.Mac -> {
                to.setPosixFilePermissions(to.getPosixFilePermissions() + PosixFilePermission.OWNER_EXECUTE)
            }
            SystemInfo.Os.Windows -> {}
        }
    } catch (e: IOException) {
        error("Unable to download $url: ${e.message}")
    }

}