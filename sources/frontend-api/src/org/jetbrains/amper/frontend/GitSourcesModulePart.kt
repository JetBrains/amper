/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import build.kargo.frontend.schema.GitSource

/**
 * Module part that holds Git-backed source definitions parsed from `sources:` block in module.yaml.
 *
 * Accessible via `module.parts.filterIsInstance<GitSourcesModulePart>()`.
 */
data class GitSourcesModulePart(
    val gitSources: List<GitSource>
) : ModulePart<GitSourcesModulePart>
