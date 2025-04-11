/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.test.Dirs
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmperVersionsCatalogTest {
    @Test
    fun catalogInAlphabeticalOrder() {
        val catalog = readCatalog()
        assertAlphabeticalOrder(catalog.versionLines, "Catalog versions")
        assertAlphabeticalOrder(catalog.libraryLines, "Catalog libraries")
    }

    // Example:
    // kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect", version.ref = "kotlin" }
    private val libraryWithVersionRef = Regex("""[^=]+=\s*\{\s*module\s*=\s*"[^"]+"\s*,\s*version\.ref\s*=\s*"[^"]+"\s*}""")

    @Test
    fun catalogLibrariesHaveExtractedVersion() {
        readCatalog().libraryLines.forEach {
            assertTrue(
                actual = it.matches(libraryWithVersionRef),
                message = "Gradle version catalog Amper convention not respected.\n" +
                        "Our Gradle version catalog (libs.versions.toml) contains the following library declaration:\n" +
                        "$it\n" +
                        "To follow our conventions, it should match the format:\n" +
                        "lib-name = { module = \"<group>:<name>\", version.ref = \"<version_var_name>\" }\n" +
                        "Make sure the version is extracted to the [versions] section.",
            )
        }
    }

    private class GradleCatalog(val versionLines: List<String>, val libraryLines: List<String>)

    private fun readCatalog(): GradleCatalog {
        val catalogFile = Dirs.amperCheckoutRoot.resolve("gradle/libs.versions.toml")
        val catalogLines = catalogFile.readLines().map { it.trim() }
        val versionLines = catalogLines.extractTomlSectionLines("versions")
        val libraryLines = catalogLines.extractTomlSectionLines("libraries")
        return GradleCatalog(versionLines, libraryLines)
    }

    private val tomlHeaderRegex = Regex("""^\[([^]]+)]$""")

    private fun List<String>.extractTomlSectionLines(sectionName: String) = dropWhile { it != "[$sectionName]" }
        .drop(1) // drop the header
        .takeWhile { !it.matches(tomlHeaderRegex) } // stop at the next header
        .filter { it.isNotBlank() }
        .filter { !it.startsWith("#") } // ignore comments
}
