/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.attributes

sealed class DependencyBundling(override val value: String) : AttributeValue {
    companion object : Attribute<DependencyBundling> {
        override val name: String = "org.gradle.dependency.bundling"

        override fun fromString(value: String): DependencyBundling = when (value) {
            External.value -> External
            Embedded.value -> Embedded
            Shadowed.value -> Shadowed
            else -> Other(value)
        }
    }

    object External : DependencyBundling("external")
    object Embedded : DependencyBundling("embedded")
    object Shadowed : DependencyBundling("shadowed")
    class Other(value: String) : DependencyBundling(value)
}
