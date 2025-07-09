package org.jetbrains.amper.plugins.buildkonfig

import org.jetbrains.amper.*

@Schema
interface Schema1 {
    /**
     * A map of properties to their values.
     * These properties will be generated
     */
    val config: Map<String, String>

    val packageName: String

    val objectName: String

    val visibility: Visibility?
}

enum class Visibility {
    `internal`,
    `public`,
}