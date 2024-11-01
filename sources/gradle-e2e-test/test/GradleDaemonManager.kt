/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.walk
import kotlin.jvm.optionals.getOrNull

object GradleDaemonManager : BeforeEachCallback, AfterTestExecutionCallback {

    private const val usedDaemonKey = "Used gradle daemon"

    private const val numberOfDaemons = 4

    init {
        println("E2E tests using $numberOfDaemons gradle test daemons")
    }

    fun deleteFileOrDirectoryOnExit(path: Path) {
        filesOrDirectoriesToBeDeletedOnShutdown.add(path)
    }

    // fun read: https://github.com/gradle/gradle/issues/12535
    private val filesOrDirectoriesToBeDeletedOnShutdown: MutableList<Path> by lazy {
        val list = Collections.synchronizedList(mutableListOf<Path>())

        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            // ensure all gradle daemons are stopped
            DefaultGradleConnector.close()

            // delete our temp files
            for (path in list.toList()) {
                if (!path.exists()) {
                    continue
                }

                println("Removing $path")

                try {
                    // delete what we can
                    if (path.isDirectory()) {
                        for (eachFile in path.walk()) {
                            try {
                                eachFile.deleteExisting()
                            } catch (t: Throwable) {
                                System.err.println("Unable to remove '$eachFile': ${t.message}")
                            }
                        }
                    }

                    // delete directory structure if possible
                    path.deleteRecursively()
                } catch (t: Throwable) {
                    System.err.println("Unable to remove '$path': ${t.message}")
                }
            }
        })

        list
    }

    private val availableGradleDaemons = ArrayBlockingQueue<GradleConnector>(numberOfDaemons).apply {
        repeat(numberOfDaemons) {
            val gradleHome = TestUtil.sharedTestCaches.resolve("gradleHome").createDirectories()
            add(GradleConnector.newConnector().useGradleUserHomeDir(gradleHome.toFile()))
        }
    }

    override fun beforeEach(context: ExtensionContext) {
        val testInstance = context.testInstance.getOrNull() as? GradleE2ETestFixture ?: return
        val takenDaemon = availableGradleDaemons.take()
        context.store.put(usedDaemonKey, takenDaemon)
        testInstance.gradleRunner = takenDaemon
    }

    override fun afterTestExecution(context: ExtensionContext) {
        val usedDaemon = context.store.remove(usedDaemonKey, GradleConnector::class.java)
        if (!availableGradleDaemons.offer(usedDaemon)) error("Unreachable")
    }

    private val ExtensionContext.store get() = getStore(namespace)
    private val ExtensionContext.namespace get() = ExtensionContext.Namespace.create(javaClass, requiredTestMethod)
}