// AUTO-MANAGED SOURCE FILE! DO NOT EDIT MANUALLY!
// --------------------------------------------
// Run ExtensibilityApiDeclarationsTest to see if the source needs updating.
//
// @formatter:off
//
@file:Suppress(
    "RedundantVisibilityModifier",
    "CanConvertToMultiDollarString",
)

package org.jetbrains.amper.frontend.plugins.generated

import java.nio.`file`.Path
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.reflect.KClass
import org.jetbrains.amper.frontend.api.FromKeyAndTheRestIsNested
import org.jetbrains.amper.frontend.api.IgnoreForSchema
import org.jetbrains.amper.frontend.api.PathMark
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.plugins.schema.model.InputOutputMark

/**
 * Generated!
 * Shadow for `org.jetbrains.amper.Dependency`
 */
public sealed class ShadowDependency : SchemaNode()

/**
 * Generated!
 * Shadow for `org.jetbrains.amper.Classpath`
 */
@SchemaDoc(doc = "Use to get a resolved JVM classpath for the list of [dependencies].\n\nThe resulting classpath can be obtained via [resolvedFiles] property.")
public class ShadowClasspath : SchemaNode() {
    @Shorthand
    @SchemaDoc(doc = "Dependencies to resolve.\nVersion conflict resolution may apply if necessary for the given list of dependencies.\n\n")
    public val dependencies: List<ShadowDependency> by value()

    @IgnoreForSchema
    @SchemaDoc(doc = "Resolved classpath files.")
    public lateinit var resolvedFiles: List<Path>
}

/**
 * Generated!
 * Shadow for `org.jetbrains.amper.Dependency.Local`
 */
@SchemaDoc(doc = "A dependency on a local module in the project.")
public class ShadowDependencyLocal : ShadowDependency() {
    @FromKeyAndTheRestIsNested
    @PathMark(InputOutputMark.ValueOnly)
    @SchemaDoc(doc = "Path to the module root directory.\n\nMust start with the `\".\"` symbol in YAML, e.g. `\"../module-name\"`, or `\".\"`.\nJust `\"module-name\"` is treated like an external [Maven] dependency.")
    public val modulePath: Path by value()
}

/**
 * Generated!
 * Shadow for `org.jetbrains.amper.Dependency.Maven`
 */
@SchemaDoc(doc = "External maven dependency.")
public class ShadowDependencyMaven : ShadowDependency() {
    @FromKeyAndTheRestIsNested
    @SchemaDoc(doc = "Maven coordinates, in the `\"<group>:<name>:<version>\"` format.")
    public val coordinates: String by value()
}

/**
 * Generated!
 * Shadow for `org.jetbrains.amper.ModuleSources`
 */
@SchemaDoc(doc = "Use to get module [source directories][sourceDirectories] from the module.\nTakes the source layout option into account.\n\nCurrently, only JVM non-test sources are supported.")
public class ShadowModuleSources : SchemaNode() {
    @SchemaDoc(doc = "Module to get source directories for.")
    public val from: ShadowDependencyLocal by nested()

    @IgnoreForSchema
    @SchemaDoc(doc = "Kotlin/Java source directories for the [module][from].\nThere can be multiple source directories for the module.\nNot all of them may exist.")
    public lateinit var sourceDirectories: List<Path>
}

public object ShadowMaps {
    public val PublicInterfaceToShadowNodeClass: Map<String, KClass<*>> = mapOf(
            "org.jetbrains.amper.Classpath" to ShadowClasspath::class,
            "org.jetbrains.amper.Dependency.Local" to ShadowDependencyLocal::class,
            "org.jetbrains.amper.Dependency.Maven" to ShadowDependencyMaven::class,
            "org.jetbrains.amper.ModuleSources" to ShadowModuleSources::class,
            "org.jetbrains.amper.Dependency" to ShadowDependency::class,
            )

    public val ShadowNodeClassToPublicReflectionName: Map<KClass<*>, String> = mapOf(
            ShadowClasspath::class to "org.jetbrains.amper.Classpath",
            ShadowDependencyLocal::class to "org.jetbrains.amper.Dependency${'$'}Local",
            ShadowDependencyMaven::class to "org.jetbrains.amper.Dependency${'$'}Maven",
            ShadowModuleSources::class to "org.jetbrains.amper.ModuleSources",
            ShadowDependency::class to "org.jetbrains.amper.Dependency",
            )
}
