/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins.generated

/**
 * A version catalog dependency node for [ShadowDependency].
 */
class ShadowDependencyCatalog : ShadowDependency() {
    val catalogKey: String by value()
}