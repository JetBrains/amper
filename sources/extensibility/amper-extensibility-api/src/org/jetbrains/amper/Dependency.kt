/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

import java.nio.file.Path

/**
 * Amper dependency sealed interface.
 */
@Schema
sealed interface Dependency {
    /**
     * A dependency on a local module in the project.
     */
    @Schema
    interface Local : Dependency {
        /**
         * Path to the module root directory.
         *
         * Must start with the `"."` symbol in YAML, e.g. `"../module-name"`, or `"."`.
         * Just `"module-name"` is treated like an external [Maven] dependency.
         */
        @PathValueOnly
        @DependencyNotation
        val modulePath: Path
    }

    /**
     * External maven dependency.
     */
    @Schema
    interface Maven : Dependency {
        /**
         * Maven coordinates, in the `"<group>:<name>:<version>"` format.
         */
        @DependencyNotation
        val coordinates: String
    }
}