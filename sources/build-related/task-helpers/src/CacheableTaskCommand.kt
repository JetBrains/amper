import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import kotlin.io.path.div

/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

abstract class CacheableTaskCommand : SuspendingCliktCommand() {

    protected val taskOutputDirectory by option("--output-dir")
        .path(canBeDir = true, canBeFile = false, mustBeWritable = true)
        .required()

    override suspend fun run() {
        // fake build output root under our task
        ExecuteOnChangedInputs(taskOutputDirectory / "incremental.state").runCached()
    }

    abstract suspend fun ExecuteOnChangedInputs.runCached()
}
