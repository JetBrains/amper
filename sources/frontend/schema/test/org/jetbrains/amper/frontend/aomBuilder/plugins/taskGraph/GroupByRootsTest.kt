/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupByRootsTest {
    @Test
    fun `example from the docs`() {
        val result = listOf(
            Path("/foo/"),
            Path("/foo/bar/"),
            Path("/foo/baz/"),
            Path("/foo/bar/quu/"),
            Path("/duu/"),
            Path("/duu/2/"),
            Path("/goo/"),
        ).groupByRoots { it }

        assertEquals(
            expected = mapOf(
                Path("/foo/") to listOf(Path("/foo/"), Path("/foo/bar/"), Path("/foo/baz/"), Path("/foo/bar/quu/")),
                Path("/duu/") to listOf(Path("/duu/"), Path("/duu/2/")),
                Path("/goo/") to listOf(Path("/goo/")),
            ),
            actual = result,
        )
    }

    @Test
    fun `empty input returns empty map`() {
        val result = emptyList<Path>().groupByRoots { it }
        assertEquals(emptyMap(), result)
    }

    @Test
    fun `single element becomes its own root`() {
        val p = Path("/only/")
        val result = listOf(p).groupByRoots { it }
        assertEquals(mapOf(p to listOf(p)), result)
    }

    @Test
    fun `siblings without parent present are separate roots`() {
        val a = Path("/a/b/")
        val b = Path("/a/c/")
        // Note: "/a/" is not present in the input, so each sibling should be its own root
        val result = listOf(a, b).groupByRoots { it }
        assertEquals(
            expected = mapOf(
                a to listOf(a),
                b to listOf(b),
            ),
            actual = result,
        )
    }

    @Test
    fun `duplicates are preserved within groups`() {
        val root = Path("/root/")
        val child = Path("/root/child/")
        val result = listOf(root, child, child, root).groupByRoots { it }
        assertEquals(
            expected = mapOf(
                root to listOf(root, child, child, root),
            ),
            actual = result,
        )
    }

    private data class Item(
        val name: String,
        val path: Path,
    )

    @Test
    fun `works with custom selector and keeps original elements`() {
        val items = listOf(
            Item("r", Path("/r/")),
            Item("a", Path("/r/a/")),
            Item("b", Path("/r/b/")),
            Item("deep", Path("/r/a/deep/")),
        )

        val result = items.groupByRoots { it.path }

        assertEquals(
            expected = mapOf(
                Path("/r/") to listOf(
                    Item("r", Path("/r/")),
                    Item("a", Path("/r/a/")),
                    Item("b", Path("/r/b/")),
                    Item("deep", Path("/r/a/deep/")),
                ),
            ),
            actual = result,
        )
    }
}