package org.jetbrains.deft.proto.gradle.compose

import org.jetbrains.compose.ComposePlugin
import org.jetbrains.deft.proto.gradle.base.BindingPluginPart
import org.jetbrains.deft.proto.gradle.base.DeftNamingConventions
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.kmpp.KMPEAware
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun applyComposeAttributes(ctx: PluginPartCtx) = ComposePluginPart(ctx).apply()

class ComposePluginPart(ctx: PluginPartCtx) : KMPEAware, DeftNamingConventions, BindingPluginPart by ctx {
    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    fun apply() {
        project.plugins.apply("org.jetbrains.compose")

        kotlinMPE.sourceSets.findByName("commonMain")?.dependencies {
            implementation(ComposePlugin.Dependencies(project).runtime)
        }
    }
}
