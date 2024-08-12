/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import kotlin.test.Test
import kotlin.test.assertEquals

class GlobTest {

    @Test
    fun normalizeGlob_preservePathsWithoutDots() {
        assertNormalized("dir", "dir")
        assertNormalized("dir1/dir2", "dir1/dir2")
    }

    @Test
    fun normalizeGlob_trimsSlashes() {
        assertNormalized("dir", "dir/")
        assertNormalized("dir", "dir//")
        assertNormalized("dir1/dir2", "dir1/dir2/")
        assertNormalized("dir1/dir2", "dir1/dir2//")
        assertNormalized("dir1/dir2", "dir1//dir2")
    }

    @Test
    fun normalizeGlob_removesSingleDots() {
        assertNormalized("dir", "./dir")
        assertNormalized("dir", "././dir")
        assertNormalized("dir", "./././dir")
        assertNormalized("dir", "dir/.")
        assertNormalized("dir", "dir/./.")
        assertNormalized("dir", "dir/././.")
        assertNormalized("dir", "./dir/.")
        assertNormalized("dir", "././dir/.")
        assertNormalized("dir", "././dir/./.")
        assertNormalized("dir1/dir2", "dir1/./dir2")
        assertNormalized("dir1/dir2", "dir1/././dir2")
        assertNormalized("dir1/dir2", "dir1/./././dir2")
    }

    @Test
    fun normalizeGlob_preservesLeadingDoubleDots() {
        assertNormalized("../dir", "../dir")
    }

    @Test
    fun normalizeGlob_removesDoubleDots() {
        assertNormalized("", "dir/..")
        assertNormalized("", "dir1/dir2/../..")
        assertNormalized("dir1", "dir1/dir2/..")
        assertNormalized("dir2", "dir1/../dir2")
        assertNormalized("dir1", "dir1/dir2/dir3/../..")
    }

    private fun assertNormalized(expected: String, glob: String) {
        assertEquals(expected, glob.normalizeGlob(), "'$glob' should be normalized to '$expected'")
    }
}