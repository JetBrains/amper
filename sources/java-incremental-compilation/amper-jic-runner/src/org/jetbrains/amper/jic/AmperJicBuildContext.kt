/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jic

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext
import com.intellij.tools.build.bazel.jvmIncBuilder.BuildProcessLogger
import com.intellij.tools.build.bazel.jvmIncBuilder.BuilderOptions
import com.intellij.tools.build.bazel.jvmIncBuilder.CLFlags
import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths
import com.intellij.tools.build.bazel.jvmIncBuilder.Message
import com.intellij.tools.build.bazel.jvmIncBuilder.NodeSourceSnapshot
import com.intellij.tools.build.bazel.jvmIncBuilder.ResourceGroup
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.RunnerRegistry
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.SourceSnapshotImpl
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.NodeSourcePathMapper
import org.jetbrains.jps.dependency.impl.PathSourceMapper
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.div

@Suppress("UnstableApiUsage")
internal class AmperJicBuildContext(
    val amperModuleName: String,
    val amperModuleDir: Path,
    javaSourceFiles: Collection<Path>,
    inputDigests: Collection<ByteArray>,
    val compilerOutputRoot: Path,
    val clFlags: Map<CLFlags, List<String>>,
    val jicDataDir: Path,
    classpath: List<Path>,
    javacArgs: List<String>,
    private val printToStdout: (String) -> Unit,
    private val printToStderr: (String) -> Unit,
) : BuildContext {

    @Volatile
    private var hasErrors = false

    private val pathMapper = createPathSourceMapper()
    private val buildProcessLogger = MyBuildProcessLogger()
    private val _builderOptions: BuilderOptions = BuilderOptions.create(javacArgs, emptyList())

    private val sources: NodeSourceSnapshot
    private val libraries: NodeSourceSnapshot

    init {
        // copy-pasted from the BuildContextImpl.java from intellij java-inc-builder
        val sourcesMap = HashMap<NodeSource, String>()
        val otherInputsMap = HashMap<Path, String>()
        val base64 = Base64.getEncoder().withoutPadding()
        val digestsIterator = inputDigests.map(base64::encodeToString).iterator()
        for (inputPath in javaSourceFiles) {
            val inputDigest = if (digestsIterator.hasNext()) digestsIterator.next() else ""
            if (isSourceDependency(inputPath)) {
                sourcesMap[pathMapper.toNodeSource(inputPath)] = inputDigest
            } else {
                otherInputsMap[inputPath] = inputDigest
            }
        }
        sources = SourceSnapshotImpl(sourcesMap)

        val libsMap = LinkedHashMap<NodeSource?, String?>() // for the classpath order is important
        for (classPathEntry in classpath) {
            libsMap[pathMapper.toNodeSource(classPathEntry)] = otherInputsMap.getOrDefault(classPathEntry, "")
        }
        libraries = SourceSnapshotImpl(libsMap)
    }

    override fun getTargetName(): String {
        return amperModuleName
    }

    override fun isCanceled(): Boolean {
        return false // TODO
    }

    override fun getBaseDir(): Path {
        return amperModuleDir
    }

    override fun getDataDir(): Path {
        return jicDataDir / "jic-data${DataPaths.DATA_DIR_NAME_SUFFIX}"
    }

    /**
     * The root folder where the generated class files should be put.
     */
    override fun getClassesOutput(): Path {
        return compilerOutputRoot
    }

    /**
     * The location of the jar containing the result of the compilation.
     */
    override fun getOutputZip(): Path {
        return jicDataDir / "${amperModuleName}-jvm.jar"
    }

    /**
     * The location of the ABI-jar that is used to determine the changes in libraries,
     * when this module is passed as a dependency to another module that is then compiled incrementally.
     */
    override fun getAbiOutputZip(): Path {
        return jicDataDir / "${amperModuleName}-abi.jar"
    }

    override fun getSources(): NodeSourceSnapshot = sources
    override fun getBinaryDependencies(): NodeSourceSnapshot = libraries

    // resources are handled by Amper outside of Java compilation
    override fun getResources(): Iterable<ResourceGroup?> = emptyList()

    override fun getBuilderOptions(): BuilderOptions = _builderOptions
    override fun getPathMapper(): NodeSourcePathMapper = pathMapper

    /**
     * Returns true if a full rebuild should be triggered rather than an incremental one.
     * A full rebuild still can be triggered in certain cases.
     * For example, if the number of changed files exceeds the threshold.
     * Or initially when there is no previous build data.
     *
     * We always return false because there is no use case for the "full rebuild" flag yet.
     */
    override fun isRebuild(): Boolean = false

    override fun getFlags(): Map<CLFlags, List<String>> = clFlags

    override fun getBuildLogger(): BuildProcessLogger = buildProcessLogger

    override fun report(msg: Message) {
        val message = msg.text

        if (msg.getKind() == Message.Kind.ERROR) {
            hasErrors = true
            printToStderr(message)
        } else {
            printToStdout(message)
        }
    }

    override fun hasErrors(): Boolean = hasErrors

    // copy-pasted from the BuildContextImpl.java from intellij java-inc-builder
    private fun createPathSourceMapper(): PathSourceMapper = PathSourceMapper(
        /* toFull = */ { relativePath: String ->
            val abs = baseDir.resolve(relativePath).normalize()
            abs.toString().replace(baseDir.fileSystem.separator, "/")
        },

        /* toRelative = */ { absolutePath: String ->
            val relative = baseDir.relativize(Path(absolutePath)).normalize()
            relative.toString().replace(baseDir.fileSystem.separator, "/")
        }
    )

    private fun isSourceDependency(path: Path?): Boolean {
        return path != null && RunnerRegistry.isCompilableSource(path)
    }

    private class MyBuildProcessLogger : BuildProcessLogger {
        override fun isEnabled(): Boolean {
            return true
        }

        override fun logDeletedPaths(paths: Iterable<String?>?) {
            // todo LOG debug
            //println("[JIC] Deleted paths: ${paths!!.joinToString()}")
        }

        override fun logCompiledPaths(files: Iterable<Path?>, builderId: String, description: String) {
            // todo LOG debug
            //println("[JIC] Compiled paths: ${files.joinToString()}")
        }
    }
}