/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package demo.app

import androidx.compose.ui.test.*
import shared.MainView
import kotlin.test.Test

class ComposeTest {
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun test() = runComposeUiTest {
        setContent {
            MainView()
        }

        onNodeWithText("Images").performClick()
        onAllNodesWithText("Res.drawable.", substring = true).assertCountEquals(9)

        onNodeWithText("Strings").performClick()
        onAllNodesWithText("Res.string.", substring = true).assertCountEquals(4)
        onAllNodesWithText("Res.array.", substring = true).assertCountEquals(1)
        onAllNodesWithText("Res.plurals.", substring = true).assertCountEquals(1)

        onNodeWithText("Fonts").performClick()
        onAllNodesWithText("Res.font.", substring = true).assertCountEquals(2)

        onNodeWithText("Files").performClick()
    }
}