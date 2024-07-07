/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.concurrency.StripedMutex
import org.jetbrains.amper.concurrency.withLock
import org.jetbrains.kotlin.buildtools.api.CompilationService
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinLogger
import org.jetbrains.kotlin.buildtools.api.ProjectId
import org.slf4j.Logger
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe
import kotlin.io.path.absolutePathString

/**
 * Converts this [AmperProjectRoot] to a [ProjectId] for the needs of the Kotlin compiler.
 *
 * The [ProjectId] for the Kotlin compiler needs to be unique per "root" project (not per module).
 * This is why the absolute path seems reasonable at the moment.
 */
internal fun AmperProjectRoot.toKotlinProjectId(): ProjectId {
    val rootProjectLocallyUniqueName = path.absolutePathString()
    return ProjectId.ProjectUUID(UUID.nameUUIDFromBytes(rootProjectLocallyUniqueName.toByteArray()))
}

/**
 * Loads the Kotlin Build Tools [CompilationService] implementation in the given [kotlinVersion], downloading it if
 * necessary. This operation is thread-safe so that the same compiler version is never downloaded twice, even if
 * requested concurrently.
 */
@OptIn(ExperimentalBuildToolsApi::class)
suspend fun CompilationService.Companion.loadMaybeCachedImpl(
    kotlinVersion: String,
    downloader: KotlinArtifactsDownloader,
): CompilationService {
    val classLoader = KotlinBuildToolsClassLoaderCache.getOrPut(kotlinVersion) {
        val buildToolsImplJars = downloader.downloadKotlinBuildToolsImpl(kotlinVersion)
        val urls = buildToolsImplJars.map { it.toUri().toURL() }.toTypedArray<URL>()
        URLClassLoader("KotlinBuildToolsImplClassLoader-$kotlinVersion", urls, CompilationService::class.java.classLoader)
    }
    return loadImplementation(classLoader)
}

// TODO add a mechanism (like Gradle BuildService) that allows us to know when no more tasks will need these
//  classloaders, so we know when to close them. At the moment they are closed when Amper exits.
/**
 * A thread-safe cache for Kotlin Build Tools implementation class loaders. At the moment, these class loaders are
 * kept until the end of the Amper execution.
 */
private object KotlinBuildToolsClassLoaderCache {

    private val classLoaders = ConcurrentHashMap<String, ClassLoader>()

    // There are usually very few different versions of Kotlin in the same project, but they collide easily with less
    // than 64 stripes (for example "1.8.20" and "1.9.21" hashes collide even with 32 stripes)
    private val stripedMutex = StripedMutex(stripeCount = 64)

    suspend fun getOrPut(kotlinVersion: String, createClassLoader: suspend () -> ClassLoader): ClassLoader =
        // ConcurrentMap.getOrPut guarantees atomic insert but doesn't guarantee that createClassLoader() will only be
        // called once, so without locking we could download the dependency twice and then discard one classloader
        // without closing it. The JDK computeIfAbsent() would solve this problem but doesn't support suspend functions.
        stripedMutex.withLock(kotlinVersion.hashCode()) {
            classLoaders.getOrPut(kotlinVersion) { createClassLoader() }
        }
}

/**
 * Adapts this SLF4J [Logger] to the [KotlinLogger] interface, for usage in the Kotlin Build Tools API.
 */
/*
Note: ModernTinylogLogger hardcodes class name detection to 2 levels up the stack. This is why we use an anonymous class
here. If we used a named class, the classname in the logs will be set to that wrapper class name, which is not helpful.
Now, ModernTinylogLogger still detects this file's class (KotlinBuildToolsApiKt) as class name, even if we mark this
function 'inline'. This is not ideal, but it's still better than SLF4JKotlinLogger wrapper class name, because it
mentions Kotlin Build Tools, and asKotlinLogger() is only meant for this.
 */
fun Logger.asKotlinLogger(): KotlinLogger {
    val slf4jLogger = this
    // /!\ This has to be an anonymous class, see note above this function.
    return object : KotlinLogger {
        override val isDebugEnabled: Boolean
            get() = slf4jLogger.isDebugEnabled

        override fun debug(msg: String) = slf4jLogger.debug(msg)
        override fun lifecycle(msg: String) = slf4jLogger.info(msg)
        override fun error(msg: String, throwable: Throwable?) = slf4jLogger.error(msg, throwable)
        override fun info(msg: String) = slf4jLogger.info(msg)
        override fun warn(msg: String) = slf4jLogger.warn(msg)
    }
}

class CombiningKotlinLogger(vararg logger: KotlinLogger): KotlinLogger {
    private val loggers = logger

    override val isDebugEnabled: Boolean
        get() = loggers.any { it.isDebugEnabled }

    override fun debug(msg: String) = loggers.forEach { it.debug(msg) }
    override fun error(msg: String, throwable: Throwable?) = loggers.forEach { it.error(msg, throwable) }
    override fun info(msg: String) = loggers.forEach { it.info(msg) }
    override fun lifecycle(msg: String) = loggers.forEach { it.lifecycle(msg) }
    override fun warn(msg: String) = loggers.forEach { it.warn(msg) }
}

@ThreadSafe
class ErrorsCollectorKotlinLogger: KotlinLogger {
    private val collector: MutableList<String> = mutableListOf()

    val errors: List<String>
        get() = synchronized(collector) {
            collector.toList()
        }

    override fun error(msg: String, throwable: Throwable?) {
        synchronized(collector) {
            collector.add(msg)
        }
    }

    override val isDebugEnabled: Boolean = false
    override fun debug(msg: String) = Unit
    override fun info(msg: String) = Unit
    override fun lifecycle(msg: String) = Unit
    override fun warn(msg: String) = Unit
}
