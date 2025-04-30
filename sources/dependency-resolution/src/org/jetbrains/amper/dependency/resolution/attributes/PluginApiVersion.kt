/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.attributes

class PluginApiVersion(override val value: String) : AttributeValue {
    companion object : Attribute<PluginApiVersion> {
        override val name: String = "org.gradle.plugin.api-version"

        override fun fromString(value: String): PluginApiVersion = PluginApiVersion(value)
    }
}
