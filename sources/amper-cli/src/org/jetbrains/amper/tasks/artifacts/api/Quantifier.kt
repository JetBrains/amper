/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.artifacts.api

/**
 * Expresses the quantity of the artifacts that are required to match a [ArtifactSelector].
 */
sealed interface Quantifier {
    sealed interface Multiple : Quantifier

    /**
     * Any number (including 0) of artifacts is expected
     */
    data object AnyOrNone : Multiple

    /**
     * At least one artifact is expected
     */
    data object AtLeastOne : Multiple

    /**
     * Exactly one artifact is expected
     */
    data object Single : Quantifier
}