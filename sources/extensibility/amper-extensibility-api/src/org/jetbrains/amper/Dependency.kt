/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

import java.nio.file.Path

/**
 * TODO: docs
 */
@Schema
sealed interface Dependency {
    @Schema
    interface Local : Dependency {
        @PathValueOnly
        @DependencyNotation
        val modulePath: Path
    }

    @Schema
    interface Maven : Dependency {
        @DependencyNotation
        val coordinates: String
    }
}