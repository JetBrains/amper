/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.jetbrains.amper.dependency.resolution.files.Writer
import org.jetbrains.amper.dependency.resolution.files.computeHash
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

private val logger = LoggerFactory.getLogger("dr/hash.kt")

interface Hash {
    val algorithm: HashAlgorithm
    val hash: String
}

data class SimpleHash (
    override val hash: String,
    override val algorithm: HashAlgorithm,
): Hash

class Hasher(override val algorithm: HashAlgorithm): Hash {
    private val digest = MessageDigest.getInstance(algorithm.digestName)
    internal val writer: Writer = Writer(digest::update)
    @OptIn(ExperimentalStdlibApi::class)
    override val hash: String by lazy { digest.digest().toHexString() }
    override fun toString() = "${ algorithm.digestName ?: algorithm.name }: $hash"
}

data class HashAlgorithm(val name: String) {
    internal val digestName by lazy {
        name.variants().firstNotNullOfOrNull { variant ->
            try {
                MessageDigest.getInstance(variant)
                variant
            } catch (e: NoSuchAlgorithmException) {
                logger.debug(e.toString())
                null
            }
        }
    }

    override fun toString(): String = digestName ?: name

    companion object {
        private fun String.algName() = this.replace(regex = "\\d".toRegex(), replacement = "")
        private fun String.algNumber() = this.replace(regex = "\\D".toRegex(), replacement = "").toInt()
        private fun String.variants(): List<String> {
            val algName = this.algName().lowercase()
            val algNumber = this.algNumber()
            return listOf("$algName$algNumber", "$algName-$algNumber")
                .let { it + it.map { it.uppercase() } }
        }
    }
}

internal val hashAlgorithms: List<HashAlgorithm> = listOf("sha512", "sha256", "sha1", "md5")
    .let { algorithmNames ->
        algorithmNames
            .map { HashAlgorithm(it) }
            .filter { it.digestName != null }
            .takeIf { it.isNotEmpty() }
            ?: error("hash algorithms ${algorithmNames.toSet()} are not supported")
    }

internal suspend fun Path.computeHash(): Collection<Hash> = computeHash(this) { createHashers() }

internal suspend fun computeHash(path: Path, algorithm: HashAlgorithm): Hasher =
    computeHash(path) { listOf(Hasher(algorithm)) }.single()

internal fun getSha1Algorithm(): HashAlgorithm = hashAlgorithms
    .singleOrNull { it.name == "sha1" }
    ?: error("hash algorithms sha1 is not available")

internal fun createHashers() = hashAlgorithms.map { Hasher(it) }