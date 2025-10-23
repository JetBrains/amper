package org.jetbrains.amper.plugins.buildkonfig

import org.jetbrains.amper.plugins.*

@Schema
interface Schema1 {
    /**
     * A map of properties to their values.
     * These properties will be generated
     */
    val config: Map<String, String>

    val packageName: String

    val objectName: String

    val visibility: Visibility get() = Visibility.Public

    val propertiesFileName: String get() = "konfig"
}

enum class Visibility {
    Internal,
    Public,
}