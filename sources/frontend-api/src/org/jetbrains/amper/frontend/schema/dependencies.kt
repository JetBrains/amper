/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.MavenCoordinates
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.FromKeyAndTheRestIsNested
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.frontend.api.StringSemantics
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.types.SchemaType.StringType.Semantics
import org.jetbrains.amper.frontend.userGuideUrl
import java.nio.file.Path

private const val dependenciesGuideUrl = "$userGuideUrl/dependencies"

@EnumOrderSensitive
enum class DependencyScope(
    override val schemaValue: String,
    val runtime: Boolean,
    val compile: Boolean,
    override val outdated: Boolean = false,
) : SchemaEnum {
    COMPILE_ONLY("compile-only", runtime = false, compile = true),
    RUNTIME_ONLY("runtime-only", runtime = true, compile = false),
    ALL("all", runtime = true, compile = true);
    companion object : EnumMap<DependencyScope, String>(DependencyScope::values, DependencyScope::schemaValue)
}

// TODO Break this hierarchy into two:
//  - DependencyNotation: MavenNotation, CatalogNotation, LocalNotation (in future replaced as just reference)
//  - Dependency: ScopedDependency, BomDependency (if we need any special meaning here for Bom).
//  .
//  Also, by breaking this hierarchy we can replace KspDependencies by just notation.
//  Also, it may contradict "constructor args" approach from AmperLang.
sealed class Dependency : SchemaNode()

sealed class ScopedDependency : Dependency() {

    // TODO Replace exported flag by new scope (rethink scopes).
    @Shorthand
    @SchemaDoc("Whether a dependency should be [visible as a part of a published API]($dependenciesGuideUrl/#transitivity)")
    val exported by value(false)
    
    @Shorthand
    @SchemaDoc("When the dependency should be used. Read more about the [dependency scopes]($dependenciesGuideUrl/#scopes)")
    val scope by value(DependencyScope.ALL)
}

class ExternalMavenDependency : ScopedDependency() {

    @SchemaDoc("Dependency on [a Kotlin or Java library]($dependenciesGuideUrl/#external-maven-dependencies) in a Maven repository")
    @StringSemantics(Semantics.MavenCoordinates)
    @FromKeyAndTheRestIsNested
    val coordinates by value<String>()
}

class InternalDependency : ScopedDependency() {

    @SchemaDoc("Dependency [on another module]($dependenciesGuideUrl/#module-dependencies) in the codebase")
    @FromKeyAndTheRestIsNested
    val path by value<Path>()
}

class CatalogDependency : ScopedDependency() {

    @SchemaDoc("Dependency from [a library catalog]($dependenciesGuideUrl/#library-catalogs)")
    @FromKeyAndTheRestIsNested
    val catalogKey by value<String>()
}

/**
 * Hierarchical notation for dependencies without scope, that is identical to [ScopedDependency].
 */
// TODO See TODO on [Dependency].
sealed class UnscopedDependency : Dependency()

sealed class UnscopedExternalDependency : UnscopedDependency()

class UnscopedExternalMavenDependency : UnscopedExternalDependency() {
    @FromKeyAndTheRestIsNested
    @StringSemantics(Semantics.MavenCoordinates)
    val coordinates by value<String>()
}

class UnscopedModuleDependency : UnscopedDependency() {
    @FromKeyAndTheRestIsNested
    val path by value<Path>()
}

class UnscopedCatalogDependency : UnscopedExternalDependency() {
    
    // Actual usage of this property is indirect and located within [CatalogVersionsSubstitutor] within the tree.
    // The value of this property is to provide the schema.
    @Suppress("unused")
    @FromKeyAndTheRestIsNested
    val catalogKey by value<String>()
}

sealed class UnscopedBomDependency : UnscopedDependency()

class UnscopedExternalMavenBomDependency : UnscopedBomDependency() {
    @FromKeyAndTheRestIsNested
    @StringSemantics(Semantics.MavenCoordinates)
    val coordinates by value<String>()
}

class UnscopedCatalogBomDependency : UnscopedBomDependency() {
    @FromKeyAndTheRestIsNested
    val catalogKey by value<String>()
}

sealed class BomDependency : Dependency()

class ExternalMavenBomDependency : BomDependency() {

    @SchemaDoc("Dependency on [a BOM]($dependenciesGuideUrl/#using-a-maven-bom) in a Maven repository")
    @FromKeyAndTheRestIsNested
    @StringSemantics(Semantics.MavenCoordinates)
    val coordinates by value<String>()
}

class CatalogBomDependency : BomDependency() {

    @SchemaDoc("BOM dependency from [a library catalog]($dependenciesGuideUrl/#library-catalogs)")
    @FromKeyAndTheRestIsNested
    val catalogKey by value<String>()
}

/**
 * Splits this [TraceableString] into its [MavenCoordinates] components.
 *
 * This [TraceableString] must respect the full Maven format with 2 to 4 parts delimited with `:`, and with an optional
 * packaging type appended after `@` at the end:
 *
 * ```
 * groupId:artifactId[:version][:classifier][@packagingType]
 * ```
 */
fun TraceableString.toMavenCoordinates(): MavenCoordinates {
    val coordsAndPackaging = value.trim().split("@")
    val coords = coordsAndPackaging.first().split(":")
    val packagingType = coordsAndPackaging.getOrNull(1)

    check(coords.size in 2..4) {
        "Coordinates should have between 2 and 4 parts, but got ${coords.size}: $this. " +
                "Ensure that the coordinates were properly validated in the parser."
    }
    return MavenCoordinates(
        groupId = coords[0],
        artifactId = coords[1],
        version = coords.getOrNull(2),
        classifier = coords.getOrNull(3),
        packagingType = packagingType,
        trace = this.trace,
    )
}
