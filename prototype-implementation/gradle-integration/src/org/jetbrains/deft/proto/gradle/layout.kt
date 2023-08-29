package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.gradle.base.BindingPluginPart
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.tooling.core.Extras
import org.jetbrains.kotlin.tooling.core.extrasKeyOf

// A way to mark non managed default created source sets.
object NotUserDefined
val notUserDefinedKey = extrasKeyOf<NotUserDefined>()
val KotlinSourceSet.notUserDefined get() = extras[notUserDefinedKey] != null

val BindingPluginPart.deftLayout
    get() = if (hasGradleScripts)
        project.extensions
            .findByType(DeftGradleExtension::class.java)
            ?.layout
            ?: error("[deft.layout] setting must be specified in build script!")
    else LayoutMode.DEFT

fun <T : Any> Collection<KotlinSourceSet>.markSourceSetsWith(
    key: Extras.Key<T>, mark: T
) = forEach { it.extras[key] = mark }