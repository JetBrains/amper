/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import java.io.File

fun main(args: Array<String>) {
    require(System.getProperty("xxx") == "yyy") {
        "System property 'xxx' must be 'yyy', but got: ${System.getProperty("xxx")}"
    }
    require(System.getenv("AAA") == "BB") {
        "Env \$AAA must be 'BB, but got: ${System.getenv("AAA")}"
    }
    System.err.println("Got the following args: [${args.joinToString(", ")}]")
    val (demoString, taskOutput, moduleVersion) = args
    check(demoString == "DEMO-STRING")
    File(taskOutput, "ProgramInfo.kt").writeText("""
        object ProgramInfo {
          val version = "${moduleVersion}"
        }
    """.trimIndent())
}
