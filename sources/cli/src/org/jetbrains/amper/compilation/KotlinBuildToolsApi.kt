package org.jetbrains.amper.compilation

import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.kotlin.buildtools.api.CompilationService
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinLogger
import org.jetbrains.kotlin.buildtools.api.ProjectId
import org.slf4j.Logger
import java.net.URLClassLoader
import java.util.*
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
 * Downloads the Kotlin Build Tools implementation in the given [kotlinVersion], and loads the [CompilationService]
 * from it. That service supports pure JVM and common/JVM mixed compilation with expect/actual. It doesn't support
 * JS, Native, nor other targets at the moment.
 */
@OptIn(ExperimentalBuildToolsApi::class)
internal suspend fun KotlinCompilerDownloader.downloadAndLoadCompilationService(kotlinVersion: String): CompilationService {
    val buildToolsImplJars = downloadAndExtractKotlinBuildToolsImpl(kotlinVersion)
    val urls = buildToolsImplJars.map { it.toUri().toURL() }.toTypedArray()
    // TODO maybe we should cache class loaders to avoid re-creating this every time
    // FIXME this classloader is never closed
    val classLoader = URLClassLoader("KotlinBuildToolsImplClassLoader", urls, CompilationService::class.java.classLoader)
    return CompilationService.loadImplementation(classLoader)
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
