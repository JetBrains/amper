/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.stdlib.hashing

import java.security.MessageDigest

/**
 * Returns the SHA-256 hash of these bytes.
 */
fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

/**
 * Returns the SHA-256 hash of the UTF-8 representation of this string.
 */
fun String.sha256(): ByteArray = encodeToByteArray().sha256()

/**
 * Returns the SHA-256 hash of these bytes, as a hexadecimal string.
 */
@OptIn(ExperimentalStdlibApi::class)
fun ByteArray.sha256String(): String = sha256().toHexString()

/**
 * Returns the SHA-256 hash of the UTF-8 representation of this string, as a hexadecimal string.
 */
@OptIn(ExperimentalStdlibApi::class)
fun String.sha256String(): String = sha256().toHexString()
