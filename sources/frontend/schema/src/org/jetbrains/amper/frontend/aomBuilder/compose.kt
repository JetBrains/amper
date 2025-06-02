/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.frontend.Model

/**
 * Try to find single compose version within model.
 */
fun chooseComposeVersion(model: Model) = model.modules
    .map { it.rootFragment.settings.compose }
    .filter { it.enabled }
    .mapNotNull { it.version }
    .maxByOrNull { ComparableVersion(it) }
