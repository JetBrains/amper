/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.frontend.schema.DefaultVersions
import org.jetbrains.amper.frontend.schema.DiscouragedDirectDefaultVersionAccess
import org.jetbrains.amper.test.Dirs
import kotlin.io.path.readLines
import kotlin.test.Test
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
    private val libraryWithVersionRef =
        Regex("""[^=]+=\s*\{\s*module\s*=\s*"[^"]+"\s*,\s*version\.ref\s*=\s*"[^"]+"\s*}""")

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

    @Test
    fun kotlinBtaVersionAtLeastAsHighAsKotlinDefault() {
        val versions = readCatalog().parsedVersions

        @OptIn(DiscouragedDirectDefaultVersionAccess::class)
        val defaultKotlinVersion = DefaultVersions.kotlin
        val kotlinBtaVersion = versions.getValue("kotlin-bta")
        assertTrue(
            actual = ComparableVersion(kotlinBtaVersion) >= ComparableVersion(defaultKotlinVersion),
            message = "`kotlin-bta` version ($kotlinBtaVersion) in libs.versions.toml must be at least as high as " +
                    "the default Kotlin version for users ($defaultKotlinVersion) " +
                    "defined in build-sources/project-commands/module.yaml. This guarantees that Build Tools API is" +
                    "aware of the compiler version used by default in users' projects and doesn't rely on forward" +
                    "compatibility.",
        )
    }

    // Example:
    // kotlin = "2.3.20"
    private val versionEntryRegex = Regex("""([\w-]+)\s*=\s*"([^"]+)"""")

    private val GradleCatalog.parsedVersions: Map<String, String>
        get() = versionLines.associate { line ->
            val match = versionEntryRegex.find(line) ?: error("Cannot parse version line: $line")
            match.groupValues[1] to match.groupValues[2]
        }

    private class GradleCatalog(val versionLines: List<String>, val libraryLines: List<String>)

    private fun readCatalog(): GradleCatalog {
        val catalogFile = Dirs.amperCheckoutRoot.resolve("libs.versions.toml")
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
