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
import org.jetbrains.amper.frontend.SchemaEnum
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
@SchemaDoc(doc = "TODO: docs")
public class ShadowClasspath : SchemaNode() {
    @Shorthand
    public val dependencies: List<ShadowDependency> by value()

    public val scope: ShadowClasspathScope by value(default = ShadowClasspathScope.Runtime)

    @IgnoreForSchema
    public lateinit var resolvedFiles: List<Path>
}

/**
 * Generated!
 * Shadow for `org.jetbrains.amper.Dependency.Local`
 */
public class ShadowDependencyLocal : ShadowDependency() {
    @FromKeyAndTheRestIsNested
    @PathMark(InputOutputMark.ValueOnly)
    public val modulePath: Path by value()
}

/**
 * Generated!
 * Shadow for `org.jetbrains.amper.Dependency.Maven`
 */
public class ShadowDependencyMaven : ShadowDependency() {
    @FromKeyAndTheRestIsNested
    public val coordinates: String by value()
}

/**
 * Generated!
 * Shadow for `org.jetbrains.amper.Classpath.Scope`
 */
public enum class ShadowClasspathScope(
    override val schemaValue: String,
) : SchemaEnum {
    Runtime("runtime"),
    Compile("compile"),
    ;
}

public object ShadowMaps {
    public val PublicInterfaceToShadowNodeClass: Map<String, KClass<*>> = mapOf(
            "org.jetbrains.amper.Classpath" to ShadowClasspath::class,
            "org.jetbrains.amper.Dependency.Local" to ShadowDependencyLocal::class,
            "org.jetbrains.amper.Dependency.Maven" to ShadowDependencyMaven::class,
            "org.jetbrains.amper.Dependency" to ShadowDependency::class,
            "org.jetbrains.amper.Classpath.Scope" to ShadowClasspathScope::class,
            )

    public val ShadowNodeClassToPublicReflectionName: Map<KClass<*>, String> = mapOf(
            ShadowClasspath::class to "org.jetbrains.amper.Classpath",
            ShadowDependencyLocal::class to "org.jetbrains.amper.Dependency${'$'}Local",
            ShadowDependencyMaven::class to "org.jetbrains.amper.Dependency${'$'}Maven",
            ShadowDependency::class to "org.jetbrains.amper.Dependency",
            ShadowClasspathScope::class to "org.jetbrains.amper.Classpath${'$'}Scope",
            )
}
