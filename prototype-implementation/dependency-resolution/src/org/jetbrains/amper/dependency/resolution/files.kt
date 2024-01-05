/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeBytes

interface CacheDirectory {
    fun guessPath(dependency: MavenDependency, extension: String): Path?
    fun getPath(dependency: MavenDependency, extension: String, bytes: ByteArray): Path
}

class GradleCacheDirectory(private val files: Path) : CacheDirectory {

    constructor() : this(getRootFromUserHome())

    companion object {
        private fun getRootFromUserHome() =
            Path.of(
                System.getenv("GRADLE_USER_HOME") ?: System.getProperty("user.home"),
                ".gradle/caches/modules-2/files-2.1"
            )
    }

    override fun toString(): String = "[Gradle] $files"

    override fun guessPath(dependency: MavenDependency, extension: String): Path? {
        val location = getLocation(dependency)
        val name = getName(dependency, extension)
        val pathFromVariant = fileFromVariant(dependency, name)?.let { location.resolve("${it.sha1}/${it.name}") }
        if (pathFromVariant != null) return pathFromVariant
        if (!location.toFile().exists()) return null
        return Files.walk(location, 2).filter {
            it.name == name
        }.findAny().orElse(null)
    }

    override fun getPath(dependency: MavenDependency, extension: String, bytes: ByteArray): Path {
        val location = getLocation(dependency)
        val name = getName(dependency, extension)
        val sha1 = computeHash("sha1", bytes)
        return location.resolve(sha1).resolve(name)
    }

    private fun getLocation(node: MavenDependency) = files.resolve("${node.group}/${node.module}/${node.version}")
}

class MavenCacheDirectory(private val repository: Path) : CacheDirectory {

    constructor() : this(getRootFromUserHome())

    companion object {
        private fun getRootFromUserHome() = Path.of(System.getProperty("user.home"), ".m2/repository")
    }

    override fun toString(): String = "[Maven] $repository"

    override fun guessPath(dependency: MavenDependency, extension: String): Path =
        repository.resolve(getLocation(dependency)).resolve(getName(dependency, extension))

    override fun getPath(dependency: MavenDependency, extension: String, bytes: ByteArray): Path =
        guessPath(dependency, extension)

    private fun getLocation(node: MavenDependency) =
        repository.resolve("${node.group.split('.').joinToString("/")}/${node.module}/${node.version}")
}

class DependencyFile(
    fileCache: List<CacheDirectory>,
    val dependency: MavenDependency,
    val extension: String
) {

    private val name = getName(dependency, extension)
    private val cacheDirectory =
        fileCache.find { it.guessPath(dependency, extension)?.toFile()?.exists() == true } ?: fileCache.first()
    val path: Path?
        get() = cacheDirectory.guessPath(dependency, extension)

    override fun toString(): String = path?.toString() ?: "[missing path]/$name"

    fun isDownloaded(): Boolean = path?.toFile()?.exists() == true

    fun readText(): String = path?.readText()
        ?: throw AmperDependencyResolutionException("Path doesn't exist, download the file first")

    fun download(resolver: Resolver): Boolean {
        return resolver.settings.repositories.find { download(it, resolver.settings.progress) }?.also {
            dependency.messages += Message("Downloaded from $it")
        } != null
    }

    private fun download(repository: String, progress: Progress, verify: Boolean = true): Boolean {
        val baos = ByteArrayOutputStream()
        if (download(repository, extension, baos)) {
            val bytes = baos.toByteArray()
            if (verify && !verify(bytes, repository, progress)) {
                return false
            }
            val target = cacheDirectory.getPath(dependency, extension, bytes)
            try {
                Files.createDirectories(target.parent)
                target.writeBytes(bytes)
                return true
            } catch (e: IOException) {
                dependency.messages += Message(
                    "Unable to move downloaded file",
                    e.toString(),
                    Severity.ERROR,
                )
            }
        }
        return false
    }

    private fun verify(bytes: ByteArray, repository: String, progress: Progress): Boolean {
        val algorithms = listOf("sha512", "sha256", "sha1", "md5")
        for (algorithm in algorithms) {
            val expectedHash = getOrDownloadExpectedHash(algorithm, repository, progress) ?: continue
            val actualHash = computeHash(algorithm, bytes)
            if (expectedHash != actualHash) {
                dependency.messages += Message(
                    "Hashes don't match for $algorithm",
                    "expected: $expectedHash, actual: $actualHash",
                    Severity.ERROR,
                )
            } else {
                return true
            }
        }
        dependency.messages += Message(
            "Unable to download checksums for $algorithms",
            repository,
            Severity.ERROR,
        )
        return false
    }

    private fun getOrDownloadExpectedHash(algorithm: String, repository: String, progress: Progress) =
        when (algorithm) {
            "sha512" -> fileFromVariant(dependency, name)?.sha512
            "sha256" -> fileFromVariant(dependency, name)?.sha256
            "sha1" -> fileFromVariant(dependency, name)?.sha1
            "md5" -> fileFromVariant(dependency, name)?.md5
            else -> null
        } ?: (getHashFromMavenCacheDirectory(algorithm, repository, progress)
            ?: downloadToString("$extension.$algorithm", repository, progress))?.split("\\s".toRegex())?.getOrNull(0)

    private fun getHashFromMavenCacheDirectory(algorithm: String, repository: String, progress: Progress): String? {
        if (cacheDirectory !is MavenCacheDirectory) return null
        val hashFile = DependencyFile(listOf(cacheDirectory), dependency, "$extension.$algorithm")
        return if (hashFile.isDownloaded() || hashFile.download(repository, progress, false)) {
            hashFile.readText()
        } else {
            null
        }
    }

    private fun downloadToString(extension: String, repository: String, progress: Progress): String? =
        ByteArrayOutputStream().let {
            if (download(repository, extension, it)) {
                it.toString()
            } else {
                null
            }
        }

    private fun download(it: String, extension: String, os: OutputStream): Boolean {
        val url = it +
                "/${dependency.group.replace('.', '/')}" +
                "/${dependency.module}" +
                "/${dependency.version}" +
                "/${dependency.module}-${dependency.version}.${extension}"
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { inputStream ->
                    BufferedInputStream(inputStream).use { bis ->
                        val data = ByteArray(1024)
                        var count: Int
                        while (bis.read(data, 0, 1024).also { count = it } != -1) {
                            os.write(data, 0, count)
                        }
                    }
                }
                return true
            }
        } catch (ignored: Exception) {
        }
        return false
    }
}

internal fun getName(node: MavenDependency, extension: String): String = "${node.module}-${node.version}.${extension}"

private fun fileFromVariant(dependency: MavenDependency, name: String) =
    dependency.variant?.files?.singleOrNull { it.name == name }

internal fun computeHash(algorithm: String, bytes: ByteArray): String {
    val messageDigest = MessageDigest.getInstance(algorithm)
    messageDigest.update(bytes, 0, bytes.size)
    val hash = messageDigest.digest()
    return toHex(hash)
}

private fun toHex(bytes: ByteArray): String {
    val result = StringBuilder()
    for (b in bytes) {
        result.append(((b.toInt() and 0xFF) + 0x100).toString(16).substring(1))
    }
    return result.toString()
}
