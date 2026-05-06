/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.cli.lazyload.ExtraClasspath
import org.jetbrains.amper.cli.logging.withoutConsoleLogging
import org.jetbrains.amper.concurrency.AsyncConcurrentMap
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinLogger
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.SharedApiClassesClassLoader
import org.slf4j.Logger
import java.net.URL
import java.net.URLClassLoader
import javax.annotation.concurrent.ThreadSafe

@OptIn(ExperimentalBuildToolsApi::class)
private val sharedBTAClassLoader by lazy { SharedApiClassesClassLoader() }

// TODO add a mechanism (like Gradle BuildService) that allows us to know when no more tasks will need these
//  classloaders, so we know when to close them. At the moment they are closed when Amper exits.
/**
 * A thread-safe cache for Kotlin Build Tools implementation class loaders. At the moment, these class loaders are
 * kept until the end of the Amper execution.
 */
// There are usually very few different versions of Kotlin in the same project, but they collide easily with less
// than 64 stripes (for example "1.8.20" and "1.9.21" hashes collide even with 32 stripes)
private val KotlinBuildToolsClassLoaderCache = AsyncConcurrentMap<String, ClassLoader>(stripeCount = 64)

/**
 * Loads the [KotlinToolchains] implementation in the given [kotlinVersion], downloading it if necessary. This
 * operation is thread-safe so that the same compiler version is never downloaded twice, even if requested concurrently.
 */
@OptIn(ExperimentalBuildToolsApi::class)
internal suspend fun KotlinToolchains.Companion.loadMaybeCachedImpl(
    kotlinVersion: String,
    downloader: KotlinArtifactsDownloader,
): KotlinToolchains {
    val classLoader = KotlinBuildToolsClassLoaderCache.computeIfAbsent(kotlinVersion) {
        val effectiveJars = buildList {
            addAll(downloader.downloadKotlinBuildToolsImpl(kotlinVersion))

            if (ComparableVersion(kotlinVersion) < ComparableVersion("2.3.0")) {
                addAll(ExtraClasspath.KOTLIN_BUILD_TOOLS_COMPAT.findJarsInDistribution())
            }
        }
        val urls = effectiveJars.map { it.toUri().toURL() }.toTypedArray<URL>()
        URLClassLoader("KotlinToolchainsClassLoader-$kotlinVersion", urls, sharedBTAClassLoader)
    }
    return loadImplementation(classLoader)
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

        // Console logging is handled by TerminalPrintingKotlinLogger or TeminalCompilerMessageRenderer depending on
        // the version of Kotlin.
        override fun debug(msg: String) = withoutConsoleLogging { slf4jLogger.debug(msg) }
        override fun lifecycle(msg: String) = withoutConsoleLogging { slf4jLogger.info(msg) }
        override fun info(msg: String) = withoutConsoleLogging { slf4jLogger.info(msg) }
        override fun warn(msg: String) = withoutConsoleLogging { slf4jLogger.warn(msg) }
        override fun warn(msg: String, throwable: Throwable?) =
            withoutConsoleLogging { slf4jLogger.warn(msg, throwable) }

        override fun error(msg: String, throwable: Throwable?) =
            withoutConsoleLogging { slf4jLogger.error(msg, throwable) }
    }
}

internal class CombiningKotlinLogger(private val loggers: List<KotlinLogger>) : KotlinLogger {

    override val isDebugEnabled: Boolean
        get() = loggers.any { it.isDebugEnabled }

    override fun debug(msg: String) = loggers.forEach { it.debug(msg) }
    override fun error(msg: String, throwable: Throwable?) = loggers.forEach { it.error(msg, throwable) }
    override fun info(msg: String) = loggers.forEach { it.info(msg) }
    override fun lifecycle(msg: String) = loggers.forEach { it.lifecycle(msg) }
    override fun warn(msg: String) = loggers.forEach { it.warn(msg) }
    override fun warn(msg: String, throwable: Throwable?) = loggers.forEach { it.warn(msg, throwable) }
}

@ThreadSafe
class ErrorsCollectorKotlinLogger : KotlinLogger {
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
    override fun warn(msg: String, throwable: Throwable?) = Unit
}

/**
 * Logger that prints Kotlin messages to the CLI.
 *
 * For Kotlin >= 2.4.0-Beta2 use [TerminalCompilerMessageRenderer] to have better structured output.
 */
internal class TerminalPrintingKotlinLogger(
    private val terminal: Terminal,
    module: AmperModule,
) : KotlinLogger {
    override val isDebugEnabled: Boolean = false

    private val moduleName = "(${module.userReadableName})"

    override fun debug(msg: String) {
        // Debug messages shouldn't be printed to the CLI
    }

    override fun lifecycle(msg: String) {
        print(style = terminal.theme.info, levelText = "LIFECYCLE", message = msg)
    }

    override fun error(msg: String, throwable: Throwable?) {
        print(style = terminal.theme.danger, levelText = "ERROR", message = msg, stderr = true)
    }

    override fun info(msg: String) {
        print(style = terminal.theme.info, levelText = "INFO", message = msg)
    }

    override fun warn(msg: String, throwable: Throwable?) {
        print(style = terminal.theme.warning, levelText = "WARN", message = msg)
    }

    private fun print(style: TextStyle, levelText: String, message: String, stderr: Boolean = false) {
        terminal.println("${TextStyles.bold(style(levelText))} ${TextStyles.italic(moduleName)} $message", stderr = stderr)
    }
}