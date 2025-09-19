/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.maven.publish

import org.apache.maven.model.Dependency
import org.apache.maven.model.DependencyManagement
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.codehaus.plexus.util.xml.XmlStreamWriter
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.BomDependency
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.MavenDependencyBase
import org.jetbrains.amper.frontend.Notation
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.ancestralPath
import org.jetbrains.amper.frontend.dr.resolver.ParsedCoordinates
import org.jetbrains.amper.frontend.dr.resolver.parseCoordinates
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Generates a POM for the given [module] at this [Path].
 */
fun Path.writePomFor(
    module: AmperModule,
    platform: Platform,
    publicationCoordsOverrides: PublicationCoordinatesOverrides,
    gradleMetadataComment: Boolean,
) {
    val model = generatePomModel(module, platform, publicationCoordsOverrides)
    writePom(model)

    if (gradleMetadataComment) {
        insertGradleMetadataComment()
    }
}

/**
 * Writes the given Maven [model] as a POM file at this [Path].
 */
fun Path.writePom(model: Model) = XmlStreamWriter(toFile()).use { writer ->
    MavenXpp3Writer().write(writer, model)
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

private fun generatePomModel(
    module: AmperModule,
    platform: Platform,
    publicationCoordsOverrides: PublicationCoordinatesOverrides,
): Model {
    val coords = module.publicationCoordinates(platform)
    val fragment = module.singleProductionFragmentOrNull(platform)
        ?: error("Cannot generate pom for module '${module.userReadableName}': expected a single fragment for platform $platform")
    // FIXME [distinct] can be error prone here, because we (I guess) have no guarantees about [externalDependencies] equality.
    val (bomDependencies, regularDependencies) = fragment.ancestralPath()
        .flatMap { it.externalDependencies }
        .distinct()
        .partition { it is BomDependency }
    val bomPomDependencies = bomDependencies.map { it.toPomDependency(platform, publicationCoordsOverrides) }
    val regularPomDependencies = regularDependencies.map { it.toPomDependency(platform, publicationCoordsOverrides) }

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
    model.dependencies.addAll(regularPomDependencies)

    if (bomDependencies.isNotEmpty()) {
        model.dependencyManagement = DependencyManagement().apply { dependencies.addAll(bomPomDependencies) }
    }

    // TODO add description for Maven Central compatibility
    // TODO add url for Maven Central compatibility
    // TODO add licenses for Maven Central compatibility
    // TODO add developers for Maven Central compatibility
    // TODO add SCM info for Maven Central compatibility

    return model
}

private fun Notation.toPomDependency(
    platform: Platform,
    publicationCoordsOverrides: PublicationCoordinatesOverrides,
): Dependency = when (this) {
    is MavenDependencyBase -> toPomDependency(publicationCoordsOverrides)
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

private fun MavenDependencyBase.toPomDependency(publicationCoordsOverrides: PublicationCoordinatesOverrides): Dependency {
    val coords = readMavenCoordinates()
    val effectiveCoordinates = publicationCoordsOverrides.actualCoordinatesFor(coords)

    val dependency = Dependency()
    dependency.groupId = effectiveCoordinates.groupId
    dependency.artifactId = effectiveCoordinates.artifactId
    dependency.version = effectiveCoordinates.version
    dependency.classifier = effectiveCoordinates.classifier
    dependency.scope = when (this) {
        is MavenDependency -> mavenScopeName()
        is BomDependency -> "import"
    }
    dependency.type = when (this) {
        is MavenDependency -> "jar"
        is BomDependency -> "pom"
    }
    return dependency
}

// TODO the knowledge of this representation should live in the frontend only, and the components should be
//  accessible in a type-safe way directly on the MavenDependency type.
private fun MavenDependencyBase.readMavenCoordinates(): MavenCoordinates {
    val parsedCoordinates = parseCoordinates()
    return when(parsedCoordinates) {
        is ParsedCoordinates.Success -> parsedCoordinates.coordinates
        is ParsedCoordinates.Failure ->  error(parsedCoordinates.messages.joinToString("\n") { it.message })
    }
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
    is MavenDependency -> coordinates.value
    is LocalModuleDependency -> module.userReadableName
    else -> toString()
}
