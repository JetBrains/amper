/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.attributes

sealed class JvmEnvironment(override val value: String) : AttributeValue {
    companion object : Attribute<JvmEnvironment> {
        override val name: String = "org.gradle.jvm.environment"

        override fun fromString(value: String): JvmEnvironment = when (value) {
            StandardJvm.value -> StandardJvm
            Android.value -> Android
            else -> Other(value)
        }
    }

    object StandardJvm : JvmEnvironment("standard-jvm")
    object Android : JvmEnvironment("android")
    class Other(value: String) : JvmEnvironment(value)
}
