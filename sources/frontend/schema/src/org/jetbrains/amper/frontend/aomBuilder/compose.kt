/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.schema.commonSettings

/**
 * Try to find single compose version within model.
 */
fun chooseComposeVersion(model: Model) = model.modules
    .map { it.origin.commonSettings.compose }
    .filter { it.enabled }
    .mapNotNull { it.version }
    .maxWithOrNull(compareBy { ComparableVersion(it) })
