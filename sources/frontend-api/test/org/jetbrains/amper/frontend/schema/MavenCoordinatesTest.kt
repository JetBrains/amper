/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.MavenCoordinates
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.TraceableString
import kotlin.test.Test
import kotlin.test.assertEquals

class MavenCoordinatesTest {

    @Test
    fun `group artifact`() {
        val coordinates = TraceableString("org.example:artifact", DefaultTrace)

        val expected = MavenCoordinates(
            groupId = "org.example",
            artifactId = "artifact",
            version = null,
            trace = DefaultTrace,
        )
        assertEquals(expected, coordinates.toMavenCoordinates())
    }

    @Test
    fun `group artifact packagingType`() {
        val coordinates = TraceableString("org.example:artifact@pom", DefaultTrace)

        val expected = MavenCoordinates(
            groupId = "org.example",
            artifactId = "artifact",
            version = null,
            packagingType = "pom",
            trace = DefaultTrace,
        )
        assertEquals(expected, coordinates.toMavenCoordinates())
    }

    @Test
    fun `group artifact version`() {
        val coordinates = TraceableString("org.example:artifact:1.0.0", DefaultTrace)

        val expected = MavenCoordinates(
            groupId = "org.example",
            artifactId = "artifact",
            version = "1.0.0",
            trace = DefaultTrace,
        )
        assertEquals(expected, coordinates.toMavenCoordinates())
    }

    @Test
    fun `group artifact version packagingType`() {
        val coordinates = TraceableString("org.example:artifact:1.2.3@jar", DefaultTrace)

        val expected = MavenCoordinates(
            groupId = "org.example",
            artifactId = "artifact",
            version = "1.2.3",
            packagingType = "jar",
            trace = DefaultTrace,
        )
        assertEquals(expected, coordinates.toMavenCoordinates())
    }

    @Test
    fun `group artifact version classifier`() {
        val coordinates = TraceableString("org.example:artifact:1.2.3:jdk11", DefaultTrace)

        val expected = MavenCoordinates(
            groupId = "org.example",
            artifactId = "artifact",
            version = "1.2.3",
            classifier = "jdk11",
            trace = DefaultTrace,
        )
        assertEquals(expected, coordinates.toMavenCoordinates())
    }

    @Test
    fun `group artifact version classifier packagingType`() {
        val coordinates = TraceableString("org.example:artifact:1.2.3:jdk11@jar", DefaultTrace)

        val expected = MavenCoordinates(
            groupId = "org.example",
            artifactId = "artifact",
            version = "1.2.3",
            classifier = "jdk11",
            packagingType = "jar",
            trace = DefaultTrace,
        )
        assertEquals(expected, coordinates.toMavenCoordinates())
    }
}
