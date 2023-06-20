package org.jetbrains.deft.proto.frontend.util

import org.jetbrains.deft.proto.frontend.Platform

private val snakeCaseRegex = "_(.)".toRegex()
private val camelCaseRegex = "(?<=.)[A-Z]".toRegex()

val Platform.fragmentName: String
    get() = name.lowercase().replace(snakeCaseRegex) { it.groupValues[1].uppercase() }

fun getPlatformFromFragmentName(fragmentName: String): Platform? {
    return try {
        Platform.valueOf(fragmentName.replace(camelCaseRegex, "_$0").uppercase())
    } catch (e: IllegalArgumentException) {
        null
    }
}

val Platform.depth: Int
    get() {
        var result = 0
        var current: Platform? = this
        while (current != null) {
            result++
            current = current.parent
        }
        return result
    }

fun findCommonParent(platform1: Platform, platform2: Platform): Platform {
    var depth1 = platform1.depth
    var depth2 = platform2.depth

    var currentP1: Platform? = platform1
    var currentP2: Platform? = platform2

    while (currentP1 != currentP2) {
        when {
            depth1 > depth2 -> {
                currentP1 = currentP1?.parent
                depth1--
            }

            depth2 > depth1 -> {
                currentP2 = currentP2?.parent
                depth2--
            }

            else -> {
                currentP1 = currentP1?.parent
                currentP2 = currentP2?.parent
            }
        }
    }

    return currentP1!!
}
