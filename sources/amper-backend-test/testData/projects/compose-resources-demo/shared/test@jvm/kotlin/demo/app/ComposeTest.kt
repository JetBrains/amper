/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package demo.app

import androidx.compose.ui.test.*
import com.example.gen.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import shared.MainView
import kotlin.test.Test
import kotlin.test.expect

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

    @Test
    @OptIn(ExperimentalResourceApi::class)
    fun `test resource collectors`() {
        expect(
            setOf(
                "compose",
                "droid_icon",
                "insta_icon",
                "land",
                "sailing",
            )
        ) {
            Res.allDrawableResources.keys
        }
    }
}