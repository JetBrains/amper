/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package demo.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import shared.MainView

fun main() =
    singleWindowApplication(
        title = "Resources demo",
        state = WindowState(size = DpSize(800.dp, 800.dp))
    ) {
        MainView()
    }
