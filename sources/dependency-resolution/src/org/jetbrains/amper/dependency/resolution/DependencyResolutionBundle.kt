/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.jetbrains.annotations.Nls
import java.text.MessageFormat
import java.util.ResourceBundle

object DependencyResolutionBundle {
    private val resourceBundle = checkNotNull(ResourceBundle.getBundle("messages.DependencyResolutionBundle")) {
        "DependencyResolutionBundle was not found in the resources"
    }

    fun message(messageKey: String, vararg arguments: Any?): @Nls String =
        MessageFormat(resourceBundle.getString(messageKey)).format(arguments)
}
