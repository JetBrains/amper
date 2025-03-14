/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.maven

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.codehaus.plexus.util.WriterFactory
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Notation
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.ancestralPath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun Path.writePomFor(module: AmperModule, platform: Platform, gradleMetadataComment: Boolean) {
    val model = generatePomModel(module, platform)
    
    WriterFactory.newXmlWriter(toFile()).use { writer ->
        MavenXpp3Writer().write(writer, model)
    }

    if (gradleMetadataComment) {
        insertGradleMetadataComment()
    }
}

@Suppress("unused")
private fun Path.insertGradleMetadataComment() {
    // Using MavenXpp3Writer.setFileComment would place the text before <project>, and as a single multiline comment,
    // which doesn't match the format output by Gradle.
    // It doesn't matter for Amper consumers, because we just search for a small substring, but it may matter for 
    // consumers using Gradle, so we're conservative and try to match the format exactly here by placing the comment
    // right before <modelVersion> and with each line as an individual XML comment.

    val contentWithGradleMetadataComment = readText().replace(
        oldValue = "  <modelVersion>",
        newValue = """
                |  <!-- This module was also published with a richer model, Gradle metadata, -->
                |  <!-- which should be used instead. Do not delete the following line which -->
                |  <!-- is to indicate to Gradle or any Gradle module metadata file consumer -->
                |  <!-- that they should prefer consuming it instead. -->
                |  <!-- do_not_remove: published-with-gradle-metadata -->
                |  <modelVersion>
                """.trimMargin()
    )
    writeText(contentWithGradleMetadataComment)
}

private fun generatePomModel(module: AmperModule, platform: Platform): Model {
    val coords = module.publicationCoordinates(platform)
    val fragment = module.singleProductionFragmentOrNull(platform)
        ?: error("Cannot generate pom for module '${module.userReadableName}': expected a single fragment for platform $platform")
    val dependencies = fragment.ancestralPath().flatMap { it.externalDependencies }

    val model = Model()
    model.modelVersion = "4.0.0"
    
    // "$groupId:$artifactId" is "common practice" for the <name> field according to Sonatype's website:
    // https://central.sonatype.org/publish/requirements/#project-name-description-and-url
    // However, but that's not what I found empirically in Maven Central in general. Rather, the module name is used.
    // TODO provide a way to override this in the frontend
    model.name = module.userReadableName
    
    model.groupId = coords.groupId
    model.artifactId = coords.artifactId
    model.version = coords.version

    model.dependencies.addAll(dependencies.map { it.toPomDependency(platform) })

    // TODO add description for Maven Central compatibility
    // TODO add url for Maven Central compatibility
    // TODO add licenses for Maven Central compatibility
    // TODO add developers for Maven Central compatibility
    // TODO add SCM info for Maven Central compatibility

    return model
}

private fun Notation.toPomDependency(platform: Platform): Dependency = when (this) {
    is MavenDependency -> toPomDependency()
    is LocalModuleDependency -> toPomDependency(platform)
    is DefaultScopedNotation -> error("Dependency type ${this::class.simpleName} is not supported for pom.xml publication")
}

private fun LocalModuleDependency.toPomDependency(platform: Platform): Dependency {
    val coords = module.publicationCoordinates(platform)

    val dependency = Dependency()
    dependency.groupId = coords.groupId
    dependency.artifactId = coords.artifactId
    dependency.version = coords.version
    dependency.scope = mavenScopeName()
    return dependency
}

private fun AmperModule.singleProductionFragmentOrNull(platform: Platform) = if (platform == Platform.COMMON) {
    fragments.singleOrNull { !it.isTest && it.fragmentDependencies.isEmpty() }   
} else {
    leafFragments.singleOrNull { !it.isTest && it.platforms == setOf(platform) }
}

private fun MavenDependency.toPomDependency(): Dependency {
    // TODO the knowledge of this representation should live in the frontend only, and the components should be
    //  accessible in a type-safe way directly on the MavenDependency type.
    val parts = coordinates.split(":")

    val dependency = Dependency()
    dependency.groupId = parts.getOrNull(0) ?: error("Missing group in dependency notation '$coordinates'")
    dependency.artifactId = parts.getOrNull(1) ?: error("Missing artifact ID in dependency notation '$coordinates'")
    dependency.version = parts.getOrNull(2) ?: error("Missing version in dependency notation '$coordinates'")
    dependency.classifier = parts.getOrNull(3)
    dependency.scope = mavenScopeName()
    return dependency
}

/**
 * Returns the Maven scope name that corresponds to the settings of this dependency, but only from the perspective of
 * consuming this dependency as a transitive dependency brought by the declaring module into some consumer module.
 * We're not interested here in using this Maven scope to compile or run the declaring module itself with Maven.
 *
 * Maven scopes are described here:
 * https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#dependency-scope
 *
 * The transitive implications are described here:
 * https://www.baeldung.com/maven-dependency-scopes#scope-and-transitivity
 *
 * In short, in a published pom, here are the implications of declaring each scope on a dependency for consumers:
 *
 * | publication \ consumer scope | compile           | runtime           | provided          |
 * | -----------------------------|-------------------|-------------------|-------------------|
 * | compile                      | C & R classpaths  | runtime classpath | runtime classpath |
 * | runtime                      | runtime classpath | runtime classpath | runtime classpath |
 * | provided                     | not visible       | not visible       | not visible       |
 *
 * Technically, there is no point in adding "provided" dependencies to the published pom.xml.
 */
private fun DefaultScopedNotation.mavenScopeName(): String = when {
    compile && runtime && exported   /* Gradle: api            */ -> "compile"
    compile && runtime && !exported  /* Gradle: implementation */ -> "runtime" // consumers should not get it on their compile classpath
    compile && !runtime && exported  /* Gradle: compileOnlyApi */ -> "compile" // Maven cannot represent this. "provided" would not be exported to consumers.
    compile && !runtime && !exported /* Gradle: compileOnly    */ -> "provided"
    !compile && runtime && exported  /* Gradle: NO EQUIVALENT  */ -> "runtime" // TODO should we forbid this case in the frontend? it doesn't make any difference to export a runtime-only dependency
    !compile && runtime && !exported /* Gradle: runtimeOnly    */ -> "runtime" // consumers should not get it on their compile classpath
    else -> error("Dependency '${userReadableCoordinates()}' is neither compile nor runtime")
}

private fun DefaultScopedNotation.userReadableCoordinates(): String = when (this) {
    is MavenDependency -> coordinates
    is LocalModuleDependency -> module.userReadableName
    else -> toString()
}
