/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

@Suppress("unused") // Still used in the IDEA plugin, let's wait until it's updated
object UsedVersions {

    // Kept because still used in ultimate.git for now
    @Deprecated(
        message = "Use the frontend settings instead, not the defaults directly. If you don't have a choice, use " +
                "`org.jetbrains.amper.frontend.schema.DefaultVersions.kotlin` from the frontend module instead.",
        replaceWith = ReplaceWith(
            expression = "DefaultVersions.kotlin",
            imports = ["org.jetbrains.amper.frontend.schema.DefaultVersions"],
        ),
    )
    val defaultKotlinVersion = "2.2.21"
}
