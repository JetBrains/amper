/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobTest {

    @Test
    fun matches_regularPaths() {
        assertGlobMatch("", "")
        assertGlobMatch("/", "/")
        assertGlobMatch("/dir", "/dir")
        assertGlobMatch("dir", "dir")
        assertGlobMatch("dir1/dir2", "dir1/dir2")
        assertGlobMatch("/dir1/dir2", "/dir1/dir2")
    }

    @Test
    fun matches_multipleSlashes() {
        assertGlobMatch("dir", "dir/")
        assertGlobMatch("dir", "dir//")
        assertGlobMatch("dir1/dir2", "dir1/dir2/")
        assertGlobMatch("dir1/dir2", "dir1/dir2//")
        assertGlobMatch("dir1/dir2", "dir1//dir2")
        assertGlobMatch("/dir1/dir2", "/dir1//dir2")
        assertGlobMatch("/dir1/dir2", "//dir1//dir2")
    }

    @Test
    fun matches_singleDots() {
        assertGlobMatch("dir", "./dir")
        assertGlobMatch("dir", "././dir")
        assertGlobMatch("dir", "./././dir")
        assertGlobMatch("dir", "dir/.")
        assertGlobMatch("dir", "dir/./.")
        assertGlobMatch("dir", "dir/././.")
        assertGlobMatch("dir", "./dir/.")
        assertGlobMatch("dir", "././dir/.")
        assertGlobMatch("dir", "././dir/./.")
        assertGlobMatch("dir1/dir2", "dir1/./dir2")
        assertGlobMatch("dir1/dir2", "dir1/././dir2")
        assertGlobMatch("dir1/dir2", "dir1/./././dir2")
    }

    @Test
    fun matches_leadingDoubleDots() {
        assertGlobMatch("../dir", "../dir")
        assertGlobMatch("../../dir", "../../dir")

        // just in case somehow the code is changed to just ignore '..' or '../..' everywhere
        assertGlobNoMatch("dir", "../dir")
        assertGlobNoMatch("dir", "../../dir")
    }

    @Test
    fun matches_doubleDots() {
        assertGlobMatch("", "dir/..")
        assertGlobMatch("", "dir1/dir2/../..")
        assertGlobMatch("dir1", "dir1/dir2/..")
        assertGlobMatch("dir2", "dir1/../dir2")
        assertGlobMatch("dir1", "dir1/dir2/dir3/../..")
    }

    @Test
    fun matches_star() {
        assertGlobMatch("dir", "*")
        assertGlobMatch("dir1/dir2", "*/*")
        assertGlobMatch("dir1/dir2", "dir1/*")
        assertGlobMatch("dir1/dir2", "*/dir2")
        assertGlobMatch("dir1/dir2/dir3", "dir1/*/dir3")
        assertGlobMatch("dir1/dir2/dir3", "dir*/*i*/*3")
    }

    @Test
    fun matches_star_doesNotMatchMultipleSegments() {
        assertGlobNoMatch("dir1/dir2", "*")
        assertGlobNoMatch("dir1/dir2/dir3", "dir1/*")
    }

    @Test
    fun matches_questionMark() {
        assertGlobMatch("dir", "?ir")
        assertGlobMatch("dir", "d?r")
        assertGlobMatch("dir", "di?")
        assertGlobMatch("dir1/dir2", "dir?/dir?")
        assertGlobMatch("dir1/dir2", "dir?/?ir2")
    }

    @Test
    fun matches_questionMark_doesNotMatchSlash() {
        assertGlobNoMatch("dir1/dir2", "dir1?dir2")
        assertGlobNoMatch("dir1/dir2/dir3", "dir1?dir2/dir3")
    }

    @Test
    fun matches_bracketExpressions() {
        assertGlobMatch("dir1", "dir[123]")
        assertGlobMatch("dir2", "dir[123]")
        assertGlobMatch("dir3", "dir[123]")
        assertGlobNoMatch("dir4", "dir[123]")
    }

    @Test
    fun matches_subpatterns() {
        assertGlobMatch("dir1", "dir{1,2}")
        assertGlobMatch("dir2", "dir{1,2}")
        assertGlobNoMatch("dir3", "dir{1,2}")

        assertGlobMatch("module.yaml", "*.{yaml,amper}")
        assertGlobMatch("module.amper", "*.{yaml,amper}")
        assertGlobMatch("project.yaml", "*.{yaml,amper}")
        assertGlobMatch("project.amper", "*.{yaml,amper}")
    }

    private fun assertGlobMatch(path: String, glob: String) {
        assertTrue(Glob(glob).matches(Path(path)), "Glob '$glob' should match path '$path'")
    }

    private fun assertGlobNoMatch(path: String, glob: String) {
        assertFalse(Glob(glob).matches(Path(path)), "Glob '$glob' should not match path '$path'")
    }
}