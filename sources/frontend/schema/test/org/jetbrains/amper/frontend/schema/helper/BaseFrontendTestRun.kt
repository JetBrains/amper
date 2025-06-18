/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.jetbrains.amper.test.golden.GoldenTest
import org.jetbrains.amper.test.golden.BaseTestRun
import kotlin.io.path.div
import kotlin.io.path.exists

/**
 * Base test, that derives standard and input paths from case name.
 */
abstract class BaseFrontendTestRun(
    caseName: String,
): BaseTestRun(caseName) {
    private val inputPostfix: String = ".yaml"
    private val inputAmperPostfix: String = ".amper"

    open val expectAmperPostfix: String = ".result.amper.txt"

    context(GoldenTest)
    override fun doTest() {
        doTest(base / "$caseName$expectPostfix", base / "$caseName$inputPostfix")

        val inputAmper = base / "$caseName$inputAmperPostfix"
        if (inputAmper.exists()) {
            val expectAmper = (base / "$caseName$expectAmperPostfix").takeIf { it.exists() }
                ?: (base / "$caseName$expectPostfix")
//            doTest(expectAmper, inputAmper)
        }
    }
}
