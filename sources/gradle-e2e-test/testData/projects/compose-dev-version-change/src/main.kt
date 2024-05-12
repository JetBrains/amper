/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import androidx.compose.ui.unit.Dp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController


// Just a check that dev version API is accessible.
@Composable
@OptIn(ExperimentalForeignApi::class)
fun getViewControllerWithCompose() = UIKitViewController<UIViewController>(
    modifier = Modifier.width(Dp.Unspecified),
    factory = { UIViewController() }
)