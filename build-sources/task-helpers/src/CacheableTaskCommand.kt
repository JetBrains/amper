/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.incrementalcache.computeClassPathHash
import kotlin.io.path.div

abstract class CacheableTaskCommand : SuspendingCliktCommand() {

    protected val taskOutputDirectory by option("--output-dir")
        .path(canBeDir = true, canBeFile = false, mustBeWritable = true)
        .required()

    override suspend fun run() {
        ExecuteOnChangedInputs(
            // We store the incremental cache for our task in our task output directory
            stateRoot = taskOutputDirectory / "incremental.state",
            // The cache should be invalidated when the code of this task changes.
            // Since tasks are executed as independent JVM processes for now, the classpath hash works perfectly.
            codeVersion = computeClassPathHash(),
        ).runCached()
    }

    abstract suspend fun ExecuteOnChangedInputs.runCached()
}
