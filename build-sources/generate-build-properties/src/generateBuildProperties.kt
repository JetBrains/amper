/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.decodeFromStream
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import kotlinx.serialization.Serializable
import org.eclipse.jgit.api.Git
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

private const val PACKAGE_NAME = "org.jetbrains.amper.buildinfo"

@TaskAction(ExecutionAvoidance.Disabled)
fun generateBuildProperties(@Output taskOutputDirectory: Path) {
    val taskOutputDirectory = taskOutputDirectory.createDirectories()

    val currentDir = Path(System.getProperty("user.dir"))
    val projectRoot = generateSequence(currentDir) { it.parent }
        .firstOrNull { it.resolve("project.yaml").exists() }
        ?: error("Project root not found: no project.yaml when looking up the parent tree from $currentDir")

    val commonModuleTemplate = projectRoot.resolve("sources/common.module-template.yaml")
    check(commonModuleTemplate.exists()) {
        "Common module template file doesn't exist: $commonModuleTemplate"
    }
    val gitRoot = projectRoot.resolve(".git")

    val version = getConfiguredPublishingVersion(commonModuleTemplate)

    // .git is usually a directory, but can be a file in the case when `git worktree add` was used so exists check
    // is sufficient.
    check(gitRoot.exists()) {
        "Git root doesn't exist: $gitRoot"
    }

    // We run without global Git config to avoid issues with people who use config parameters that are not
    // supported by JGit. For example, the 'patience' diff algorithm isn't supported.
    val (commitHash, commitShortHash, commitDate) = runWithoutGlobalGitConfig {
        Git.open(projectRoot.toFile()).use { git ->
            val repo = git.repository
            val head = repo.refDatabase.getReflogReader("HEAD").lastEntry
            val shortHash = repo.newObjectReader().use { it.abbreviate(head.newId).name() }
            Triple(head.newId.name, shortHash, head.who.whenAsInstant.toString())
        }
    }

    val fileSpec = generateBuildInfoFile(version, commitHash, commitShortHash, commitDate)
    val content = fileSpec.toString().toByteArray()
    val outputDir = taskOutputDirectory.resolve(PACKAGE_NAME.replace('.', '/'))
    outputDir.createDirectories()
    writeContentIfChanged(outputDir.resolve("AmperBuild.kt"), content)
}

private fun generateBuildInfoFile(
    version: String,
    commitHash: String,
    commitShortHash: String,
    commitDate: String,
): FileSpec {
    val instantClass = ClassName("kotlin.time", "Instant")

    val isDevVersion = version.contains("-dev-")
    val isSnapshot = version.contains("-SNAPSHOT")
    val majorAndMinorVersion = extractMajorAndMinorVersion(version)
    val docUrl = documentationUrl(version)

    val buildInfoObject = TypeSpec.objectBuilder("AmperBuild")
        .addProperty(
            PropertySpec.builder("mavenVersion", String::class)
                .addModifiers(KModifier.CONST)
                .initializer("%S", version)
                .addKdoc("The current version of Amper as seen in Maven dependencies.")
                .build()
        )
        .addProperty(
            PropertySpec.builder("majorAndMinorVersion", String::class)
                .addModifiers(KModifier.CONST)
                .initializer("%S", majorAndMinorVersion)
                .addKdoc("The first two components of the version (e.g., \"0.9\" from \"0.9.1\").")
                .build()
        )
        .addProperty(
            PropertySpec.builder("isSNAPSHOT", Boolean::class)
                .addModifiers(KModifier.CONST)
                .initializer("%L", isSnapshot)
                .build()
        )
        .addProperty(
            PropertySpec.builder("isDevVersion", Boolean::class)
                .addModifiers(KModifier.CONST)
                .initializer("%L", isDevVersion)
                .addKdoc("Whether current build is a development one.")
                .build()
        )
        .addProperty(
            PropertySpec.builder("documentationUrl", String::class)
                .addModifiers(KModifier.CONST)
                .initializer("%S", docUrl)
                .addKdoc(
                    """
                    URL to the Amper documentation for this version.

                    Note: For dev versions, this always points to the latest dev documentation
                    even if a corresponding stable version has been released.
                    """.trimIndent()
                )
                .build()
        )
        .addProperty(
            PropertySpec.builder("commitHash", String::class)
                .addModifiers(KModifier.CONST)
                .initializer("%S", commitHash)
                .build()
        )
        .addProperty(
            PropertySpec.builder("commitShortHash", String::class)
                .addModifiers(KModifier.CONST)
                .initializer("%S", commitShortHash)
                .build()
        )
        .addProperty(
            PropertySpec.builder("commitInstant", instantClass)
                .initializer("%T.parse(%S)", instantClass, commitDate)
                .build()
        )
        .build()

    return FileSpec.builder(PACKAGE_NAME, "AmperBuild")
        .addFileComment("THIS FILE IS AUTO-GENERATED. DO NOT EDIT MANUALLY.")
        .addType(buildInfoObject)
        .addAnnotation(
            // KotlinPoet generates everything with explicit public visibility
            AnnotationSpec.builder(Suppress::class)
                .addMember("%S", "REDUNDANT_VISIBILITY_MODIFIER")
                .build()
        )
        .build()
}

internal fun extractMajorAndMinorVersion(version: String): String =
    version.split("-")[0].split(".").take(2).joinToString(".")

internal fun documentationUrl(version: String): String {
    val isDevVersion = version.contains("-dev-")
    val isSnapshot = version.contains("-SNAPSHOT")
    val majorAndMinorVersion = extractMajorAndMinorVersion(version)
    return if (isDevVersion || isSnapshot) "https://amper.org/dev" else "https://amper.org/$majorAndMinorVersion"
}

private fun getConfiguredPublishingVersion(commonModuleTemplate: Path): String {
    val yamlConf = YamlConfiguration(strictMode = false)
    val yaml = Yaml(configuration = yamlConf)
    val commonTemplate = commonModuleTemplate.inputStream().use { yaml.decodeFromStream<TemplateYaml>(it) }
    return commonTemplate.settings.publishing.version
}

@Serializable
private data class TemplateYaml(val settings: SettingsYaml)

@Serializable
private data class SettingsYaml(val publishing: PublishingYaml)

@Serializable
private data class PublishingYaml(val version: String)

private fun writeContentIfChanged(file: Path, content: ByteArray) {
    if (file.exists()) {
        val existingContent = file.readBytes()
        if (content.contentEquals(existingContent)) return
    }

    file.writeBytes(content)
}
