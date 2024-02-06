/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.CollectingProblemReporter
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.aomBuilder.DefaultFioContext
import org.jetbrains.amper.frontend.aomBuilder.DumbGradleModule
import org.jetbrains.amper.frontend.aomBuilder.FioContext
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.KoverSettings
import org.jetbrains.amper.frontend.schema.NativeSettings
import org.jetbrains.amper.frontend.schema.PublishingSettings
import org.jetbrains.amper.frontend.schema.Settings
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText


class TestProblemReporter : CollectingProblemReporter() {
    override fun doReportMessage(message: BuildProblem) {}

    fun getErrors(): List<BuildProblem> = problems.filter { it.level == Level.Error || it.level == Level.Fatal }
}

class TestProblemReporterContext : ProblemReporterContext {
    override val problemReporter: TestProblemReporter = TestProblemReporter()
}

internal fun PotatoModule.prettyPrint(): String {
    return buildString {
        appendLine("Fragments:")
        for (fragment in fragments.sortedBy { it.name }) {
            appendLine("  ${fragment.name}")
            appendLine("    External dependencies:")
            for (dependency in fragment.externalDependencies.sortedBy { it.toString() }) {
                appendLine("      $dependency")
            }
            appendLine("    Src folder: ${fragment.src.fileName}")
            appendLine("    Fragment dependencies:")
            for (dependency in fragment.fragmentDependencies) {
                appendLine("      ${dependency.target.name} (${dependency.type})")
            }
            appendLine("    Parts:")
            appendLine(fragment.settings.toLegacyPartsString().trim().prependIndent("      "))
        }
        appendLine("Artifacts:")
        for (artifact in artifacts.sortedBy { it.name }) {
            appendLine("  isTest: ${artifact.isTest}")
            appendLine("  ${artifact.platforms}")
            appendLine("    Fragments:")
            for (fragment in artifact.fragments) {
                appendLine("      ${fragment.name}")
            }
        }

        val repositories = parts[RepositoriesModulePart::class.java]?.mavenRepositories
        if (!repositories.isNullOrEmpty()) {
            appendLine("Repositories:")
            repositories.forEach {
                appendLine("  - id: ${it.id}")
                appendLine("    url: ${it.url}")
                appendLine("    publish: ${it.publish}")
                appendLine("    username: ${it.userName}")
                appendLine("    password: ${it.password}")
            }
        }
    }
}

// TODO Use a visitor to generate the real settings tree instead
private fun Settings.toLegacyPartsString() = buildString {
    with(kotlin) {
        appendLine("KotlinPart(languageVersion=$languageVersion, apiVersion=$apiVersion, allWarningsAsErrors=$allWarningsAsErrors, freeCompilerArgs=${freeCompilerArgs ?: emptyList()}, suppressWarnings=$suppressWarnings, verbose=$verbose, linkerOpts=${linkerOpts ?: emptyList()}, debug=$debug, progressiveMode=$progressiveMode, languageFeatures=${languageFeatures ?: emptyList()}, optIns=${optIns ?: emptyList()}, serialization=${serialization?.format})")
    }
    with(android) {
        appendLine("AndroidPart(compileSdk=android-${compileSdk.schemaValue}, minSdk=${minSdk.schemaValue}, maxSdk=${maxSdk.schemaValue}, targetSdk=${targetSdk.schemaValue}, applicationId=$applicationId, namespace=$namespace)")
    }
    with(ios) {
        appendLine("IosPart(teamId=$teamId)")
    }
    with(java) {
        appendLine("JavaPart(source=${source?.schemaValue})")
    }
    with(jvm) {
        appendLine("JvmPart(mainClass=$mainClass, target=${target.schemaValue})")
    }
    with(junit) {
        appendLine("JUnitPart(version=$this)")
    }
    with(publishing ?: PublishingSettings()) {
        appendLine("PublicationPart(group=$group, version=$version)")
    }
    with(native ?: NativeSettings()) {
        appendLine("NativeApplicationPart(entryPoint=$entryPoint, baseName=null, debuggable=null, optimized=null, binaryOptions={}, declaredFrameworkBasename=kotlin, frameworkParams=null)")
    }
    with(compose) {
        appendLine("ComposePart(enabled=$enabled)")
    }
    with(kover ?: KoverSettings()) {
        val htmlString = html?.run { "KoverHtmlPart(title=$title, charset=$charset, onCheck=$onCheck, reportDir=$reportDir)" }
        val xmlString = xml?.run { "KoverXmlPart(onCheck=$onCheck, reportFile=$reportFile)" }
        appendLine("KoverPart(enabled=${if (enabled) true else null}, html=$htmlString, xml=$xmlString)")
    }
}

context(TestBase)
fun copyLocal(localName: String, dest: Path = buildFile, newPath: () -> Path = { dest / localName }) {
    val localFile = baseTestResourcesPath.resolve(localName).normalize().takeIf(Path::exists)
    val newPathWithDirs = newPath().apply { createDirectories() }
    localFile?.copyTo(newPathWithDirs, overwrite = true)
}

context(TestBase)
fun readContentsAndReplace(
    expectedPath: Path,
    base: Path,
): String {
    val buildDir = buildFile.normalize().toString()
    val potDir = expectedPath.toAbsolutePath().normalize().parent.toString()
    val testProcessDir = Path(".").normalize().absolutePathString()
    val testResources = Path(".").resolve(base).normalize().absolutePathString()

    // This is actual check.
    val resourceFileText = expectedPath.readText()
    return resourceFileText
        .replace("{{ buildDir }}", buildDir)
        .replace("{{ potDir }}", buildFile.parent.relativize(Path.of(potDir)).toString())
        .replace("{{ testProcessDir }}", testProcessDir)
        .replace("{{ testResources }}", testResources)
        .replace("{{ fileSeparator }}", File.separator)
}

class TestSystemInfo(
    private val predefined: SystemInfo.Os
) : SystemInfo {
    override fun detect() = predefined
}

open class TestFioContext(
    override val root: VirtualFile,
    override val amperModuleFiles: List<VirtualFile>,
    val frontendPathResolver: FrontendPathResolver,
) : FioContext by DefaultFioContext(root) {
    override val ignorePaths: MutableList<Path> = mutableListOf()
    override val gradleModules: Map<VirtualFile, DumbGradleModule> = mutableMapOf()
    val path2catalog: MutableMap<VirtualFile, VirtualFile> = mutableMapOf()
    override fun getCatalogPathFor(file: VirtualFile): VirtualFile? = path2catalog[file]
}