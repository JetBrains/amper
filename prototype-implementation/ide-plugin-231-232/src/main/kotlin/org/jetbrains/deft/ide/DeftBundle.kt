package org.jetbrains.deft.ide

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE_NAME = "messages.DeftBundle"

object DeftBundle : DynamicBundle(BUNDLE_NAME) {
    @Nls
    fun message(
        @PropertyKey(resourceBundle = BUNDLE_NAME) key: String,
        vararg params: Any
    ): String = getMessage(key, *params)
}
