package org.jetbrains.amper.frontend.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CartesianTest {

    private fun Collection<Collection<String>>.doPretty() =
        joinToString { it.joinToString(prefix = "[", postfix = "]") { it } }

    @Test
    fun `simple cartesian test`() {
        val input = listOf(
            listOf("a", "b"),
            listOf("c", "d"),
        )

        val cartesianResult = input.cartesianGeneric(
            { listOf() },
            { this },
            List<String>::plus,
            preserveLowerDimensions = false,
            preserveEmpty = false
        )

        assertEquals(
            "[a, c], [a, d], [b, c], [b, d]",
            cartesianResult.doPretty(),
        )
    }

    @Test
    fun `cartesian preserve lower test`() {
        val input = listOf(
            listOf("a", "b"),
            listOf("c", "d"),
        )

        val cartesianResult = input.cartesianGeneric(
            { listOf() },
            { this },
            List<String>::plus,
            preserveLowerDimensions = true,
            preserveEmpty = false
        )

        assertEquals(
            "[a], [b], [c], [d], [a, c], [a, d], [b, c], [b, d]",
            cartesianResult.doPretty(),
        )
    }

    @Test
    fun `cartesian preserve empty`() {
        val input = listOf(
            listOf("a", "b"),
            listOf("c", "d"),
        )

        val cartesianResult = input.cartesianGeneric(
            { listOf() },
            { this },
            List<String>::plus,
            preserveLowerDimensions = true,
            preserveEmpty = true
        )

        assertEquals(
            "[], [a], [b], [c], [d], [a, c], [a, d], [b, c], [b, d]",
            cartesianResult.doPretty(),
        )
    }

    @Test
    fun `cartesian set accumulator`() {
        val input = listOf(
            listOf("a", "b", "c"),
            listOf("a", "e"),
        )

        val cartesianResult = input.cartesianGeneric(
            { setOf() },
            { this.toSet() },
            Set<String>::plus,
            preserveLowerDimensions = false,
            preserveEmpty = false
        )

        assertEquals(
            "[a], [a, e], [b, a], [b, e], [c, a], [c, e]",
            cartesianResult.doPretty(),
        )
    }

    @Test
    fun `cartesian lots of dimensions`() {
        val input = listOf(
            listOf("11", "12", "13"),
            listOf("21", "22", "23"),
            listOf("31", "32", "33"),
            listOf("41", "42", "43"),
        )

        val cartesianResult = input.cartesianGeneric(
            { listOf() },
            { this },
            List<String>::plus,
            preserveLowerDimensions = false,
            preserveEmpty = false
        )

        assertEquals(
            // 11
            "[11, 21, 31, 41], [11, 21, 31, 42], [11, 21, 31, 43], " +
                    "[11, 21, 32, 41], [11, 21, 32, 42], [11, 21, 32, 43], " +
                    "[11, 21, 33, 41], [11, 21, 33, 42], [11, 21, 33, 43], " +
                    "[11, 22, 31, 41], [11, 22, 31, 42], [11, 22, 31, 43], " +
                    "[11, 22, 32, 41], [11, 22, 32, 42], [11, 22, 32, 43], " +
                    "[11, 22, 33, 41], [11, 22, 33, 42], [11, 22, 33, 43], " +
                    "[11, 23, 31, 41], [11, 23, 31, 42], [11, 23, 31, 43], " +
                    "[11, 23, 32, 41], [11, 23, 32, 42], [11, 23, 32, 43], " +
                    "[11, 23, 33, 41], [11, 23, 33, 42], [11, 23, 33, 43], " +
                    // 12
                    "[12, 21, 31, 41], [12, 21, 31, 42], [12, 21, 31, 43], " +
                    "[12, 21, 32, 41], [12, 21, 32, 42], [12, 21, 32, 43], " +
                    "[12, 21, 33, 41], [12, 21, 33, 42], [12, 21, 33, 43], " +
                    "[12, 22, 31, 41], [12, 22, 31, 42], [12, 22, 31, 43], " +
                    "[12, 22, 32, 41], [12, 22, 32, 42], [12, 22, 32, 43], " +
                    "[12, 22, 33, 41], [12, 22, 33, 42], [12, 22, 33, 43], " +
                    "[12, 23, 31, 41], [12, 23, 31, 42], [12, 23, 31, 43], " +
                    "[12, 23, 32, 41], [12, 23, 32, 42], [12, 23, 32, 43], " +
                    "[12, 23, 33, 41], [12, 23, 33, 42], [12, 23, 33, 43], " +
                    // 13
                    "[13, 21, 31, 41], [13, 21, 31, 42], [13, 21, 31, 43], " +
                    "[13, 21, 32, 41], [13, 21, 32, 42], [13, 21, 32, 43], " +
                    "[13, 21, 33, 41], [13, 21, 33, 42], [13, 21, 33, 43], " +
                    "[13, 22, 31, 41], [13, 22, 31, 42], [13, 22, 31, 43], " +
                    "[13, 22, 32, 41], [13, 22, 32, 42], [13, 22, 32, 43], " +
                    "[13, 22, 33, 41], [13, 22, 33, 42], [13, 22, 33, 43], " +
                    "[13, 23, 31, 41], [13, 23, 31, 42], [13, 23, 31, 43], " +
                    "[13, 23, 32, 41], [13, 23, 32, 42], [13, 23, 32, 43], " +
                    "[13, 23, 33, 41], [13, 23, 33, 42], [13, 23, 33, 43]",
            cartesianResult.doPretty(),
        )
    }
}