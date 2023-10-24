package org.jetbrains.amper.core.messages

import java.text.MessageFormat
import java.util.*

open class MessageBundle(bundleName: String) {
    private val resourceBundle = ResourceBundle.getBundle(bundleName)

    fun message(messageKey: String, vararg arguments: Any): String {
        if (!resourceBundle.containsKey(messageKey)) {
            return messageKey
        }
        return MessageFormat(resourceBundle.getString(messageKey)).format(arguments)
    }
}
