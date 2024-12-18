/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package package1

import kotlinx.datetime.toLocalTime
import com.example.shared.getWorld
import Utils

fun main(args: Array<String>) {
    val hour = "12:01:03".toLocalTime().hour
    println(Utils.superCommonMethod() + " Multiplatform CLI $hour: ${getWorld()}")
    for ((index, arg) in args.withIndex()) {
        println("ARG${index}: <$arg>")
    }
}
