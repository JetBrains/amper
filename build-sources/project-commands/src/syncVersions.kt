/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ReplacePrintlnWithLogging")

import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction
import utils.fetchContent
import utils.replaceEachFileText
import utils.replaceFileText
import utils.replaceRegexGroup1
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.visitFileTree

/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@TaskAction(ExecutionAvoidance.Disabled) // we can't track all outputs
fun syncVersions(@Input amperRootDir: Path, versions: Versions) {
    VersionUpdater(amperRootDir).syncVersions(versions)
}

class VersionUpdater(val amperRootDir: Path) {
    val amperMavenRepoUrl = "https://packages.jetbrains.team/maven/p/amper/amper"

    val amperWrapperModuleDir = amperRootDir / "sources/amper-wrapper"
    val versionsCatalogToml = amperRootDir / "libs.versions.toml"
    val defaultVersionsKt = amperRootDir / "sources/frontend-api/src/org/jetbrains/amper/frontend/schema/DefaultVersions.kt"

    fun syncVersions(versions: Versions) {
        println("Making sure user-visible versions are aligned in Amper, docs, and examples...")
        updateVersionsCatalog(versions)
        updateDefaultVersionsKt(versions.defaultsForUsers)
        updateAmperWrappers(versions)
        updateWrapperTemplates(versions)
        println("Done.")
    }

    private fun updateVersionsCatalog(versions: Versions) {
        versionsCatalogToml.replaceFileText { text ->
            text
                // we align our Kotlin BTA and AA version with the default Kotlin version
                .replaceCatalogVersionVariable(variableName = "kotlin", newValue = versions.defaultsForUsers.kotlin)
                .replaceCatalogVersionVariable(variableName = "compose-hot-reload-version", newValue = versions.defaultsForUsers.composeHotReload)
                .replaceCatalogVersionVariable(variableName = "junit5-platform", newValue = versions.minSupportedJUnitPlatform)
        }
    }

    private fun String.replaceCatalogVersionVariable(variableName: String, newValue: String): String = replaceRegexGroup1(
        regex = Regex("""^${Regex.escape(variableName)}\s*=\s*"([^"]+)"""", RegexOption.MULTILINE),
        replacement = newValue,
    )

    private fun updateDefaultVersionsKt(defaultVersions: DefaultVersions) {
        defaultVersionsKt.replaceFileText { text ->
            text
                .replaceDefaultVersionVariable(variableName = "compose", newValue = defaultVersions.compose)
                .replaceDefaultVersionVariable(variableName = "composeHotReload", newValue = defaultVersions.composeHotReload)
                .replaceDefaultVersionIntVariable(variableName = "jdk", newValue = defaultVersions.jdk)
                .replaceDefaultVersionVariable(variableName = "junitPlatform", newValue = defaultVersions.junitPlatform)
                .replaceDefaultVersionVariable(variableName = "kotlin", newValue = defaultVersions.kotlin)
                .replaceDefaultVersionVariable(variableName = "kotlinxRpc", newValue = defaultVersions.kotlinxRpc)
                .replaceDefaultVersionVariable(variableName = "kotlinxSerialization", newValue = defaultVersions.kotlinxSerialization)
                .replaceDefaultVersionVariable(variableName = "ksp", newValue = defaultVersions.ksp)
                .replaceDefaultVersionVariable(variableName = "ktor", newValue = defaultVersions.ktor)
                .replaceDefaultVersionVariable(variableName = "lombok", newValue = defaultVersions.lombok)
                .replaceDefaultVersionVariable(variableName = "springBoot", newValue = defaultVersions.springBoot)
        }
    }

    private fun String.replaceDefaultVersionVariable(variableName: String, newValue: String): String = replaceRegexGroup1(
        regex = Regex("""/\*managed_default\*/\s*val\s+${Regex.escape(variableName)}\s*=\s*"([^"]+)""""),
        replacement = newValue,
    )

    private fun String.replaceDefaultVersionIntVariable(variableName: String, newValue: String): String = replaceRegexGroup1(
        regex = Regex("""/\*managed_default\*/\s*val\s+${Regex.escape(variableName)}\s*=\s*(\d+)"""),
        replacement = newValue,
    )

    private fun updateAmperWrappers(versions: Versions) {
        val shellWrapperText =
            fetchContent("$amperMavenRepoUrl/org/jetbrains/amper/amper-cli/${versions.bootstrapAmperVersion}/amper-cli-${versions.bootstrapAmperVersion}-wrapper")
        val batchWrapperText =
            fetchContent("$amperMavenRepoUrl/org/jetbrains/amper/amper-cli/${versions.bootstrapAmperVersion}/amper-cli-${versions.bootstrapAmperVersion}-wrapper.bat")

        amperRootDir.forEachWrapperFile { path ->
            when (path.name) {
                "amper" -> path.replaceFileText { shellWrapperText }
                "amper.bat" -> path.replaceFileText { batchWrapperText }
            }
        }
    }

    private val excludedDirs = setOf("build", "build-from-sources", ".gradle", ".kotlin", ".git", "shared test caches")

    private fun Path.forEachWrapperFile(action: (Path) -> Unit) {
        visitFileTree {
            onPreVisitDirectory { dir, _ ->
                if (dir.name in excludedDirs) {
                    FileVisitResult.SKIP_SUBTREE
                } else {
                    FileVisitResult.CONTINUE
                }
            }
            onVisitFile { file, _ ->
                if (file.name in setOf("amper", "amper.bat")) {
                    action(file)
                }
                FileVisitResult.CONTINUE
            }
        }
    }

    private fun updateWrapperTemplates(versions: Versions) {
        val zuluVersion = versions.amperJre.zuluDistro
        val javaVersion = versions.amperJre.java

        val jres = AzulApi.getZuluJreChecksums(zuluVersion, javaVersion)

        sequenceOf(
            amperWrapperModuleDir / "resources/wrappers/launcher.template.sh",
            amperRootDir / "amper-from-sources",
        ).replaceEachFileText { initialText ->
            val textWithVersion = initialText
                .replaceRegexGroup1(Regex("""^\s*zulu_version=(\S+)""", RegexOption.MULTILINE), zuluVersion)
                .replaceRegexGroup1(Regex("""^\s*java_version=(\S+)""", RegexOption.MULTILINE), javaVersion)
            jres.fold(textWithVersion) { text, (os, arch, checksum) ->
                text.replaceRegexGroup1(Regex(""""${os.filenameValue} ${arch.filenameValue}"\)\s+jre_sha256=(\w+)"""), checksum)
            }
        }

        sequenceOf(
            amperRootDir / "amper-from-sources.bat",
        ).replaceEachFileText { initialText ->
            val textWithVersion = initialText
                .replaceRegexGroup1(Regex("""^\s*set\s+zulu_version=(\S+)""", RegexOption.MULTILINE), zuluVersion)
                .replaceRegexGroup1(Regex("""^\s*set\s+java_version=(\S+)""", RegexOption.MULTILINE), javaVersion)
            jres.filter { it.os == AzulApi.Os.Windows }.fold(textWithVersion) { text, (_, arch, checksum) ->
                text.replaceRegexGroup1(Regex("""set jre_arch=${arch.filenameValue}\s+set jre_sha256=(\S+)""", RegexOption.MULTILINE), checksum)
            }
        }
    }

}