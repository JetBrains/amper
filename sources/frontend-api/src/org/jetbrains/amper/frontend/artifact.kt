/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend


/**
 * Some resulting artifact that is build from several leaf fragments.
 */
interface Artifact {
    val name: String
    val fragments: List<LeafFragment>
    val platforms: Set<Platform>
    val isTest: Boolean
}