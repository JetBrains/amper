/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.util

import org.jetbrains.amper.frontend.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformUtilTest {
    @Test
    fun `get platform from fragment name`() {
        assertEquals(Platform.MINGW_X64, getPlatformFromFragmentName("mingwX64"))
        assertEquals(Platform.ANDROID_NATIVE, getPlatformFromFragmentName("androidNative"))
        assertEquals(Platform.IOS_SIMULATOR_ARM64, getPlatformFromFragmentName("iosSimulatorArm64"))
    }

    @Test
    fun `get platform's fragment name`() {
        assertEquals("iosSimulatorArm64", Platform.IOS_SIMULATOR_ARM64.fragmentName)
        assertEquals("tvosX64", Platform.TVOS_X64.fragmentName)
        assertEquals("androidNativeX86", Platform.ANDROID_NATIVE_X86.fragmentName)
    }

    @Test
    fun `find common parent`() {
        assertEquals(Platform.APPLE, findCommonParent(Platform.WATCHOS_SIMULATOR_ARM64, Platform.TVOS))
        assertEquals(Platform.COMMON, findCommonParent(Platform.ANDROID, Platform.IOS_ARM64))
        assertEquals(Platform.NATIVE, findCommonParent(Platform.MACOS_ARM64, Platform.MINGW_X64))
    }
}