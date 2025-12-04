/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.options

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path

class ProjectLayoutOptions : OptionGroup("Project layout options") {

    /**
     * The explicit project root directory provided by the user, or null if the root should be discovered.
     */
    val explicitProjectDir by option(
        "--project-dir",
        help = "The root directory of the project. By default, this is discovered automatically by looking up the " +
                "file tree starting from the current directory.",
    ).path(mustExist = true, canBeFile = false, canBeDir = true)

    /**
     * The explicit build directory provided by the user, or null if the build dir should be the default for the
     * current project root.
     */
    val explicitBuildDir by option(
        "--build-dir",
        help = "The root directory for all build outputs. " +
                "By default, this is the `build` directory under the project root.",
        envvar = "AMPER_BUILD_DIR",
    )
        .path(mustExist = false, canBeFile = false, canBeDir = true)
}
