/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

object MainConsumer {
    @JvmStatic
    fun main(args: Array<String>) {
        check(javaClass.getResourceAsStream("build.properties")!!
              .use { it.readAllBytes().decodeToString() } == "build.props")
        check(javaClass.getResourceAsStream("some/my.file")!!
              .use { it.readAllBytes().decodeToString() } == "my")
        println("Resources OK")
    }
}
