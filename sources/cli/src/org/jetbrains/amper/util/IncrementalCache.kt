/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs

fun ExecuteOnChangedInputs(buildOutputRoot: AmperBuildOutputRoot): ExecuteOnChangedInputs =
    ExecuteOnChangedInputs(buildOutputRoot.path.resolve("incremental.state"), AmperBuild.codeIdentifier)
