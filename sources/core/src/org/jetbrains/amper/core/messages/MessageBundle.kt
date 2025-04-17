/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.messages

import org.jetbrains.annotations.Nls
import java.text.MessageFormat
import java.util.*

open class MessageBundle(bundleName: String) {
    private val resourceBundle = ResourceBundle.getBundle(bundleName)

    fun message(messageKey: String, vararg arguments: Any?): @Nls String {
        if (!resourceBundle.containsKey(messageKey)) {
            return messageKey
        }
        return MessageFormat(resourceBundle.getString(messageKey)).format(arguments)
    }
}
