package org.jetbrains.deft.proto.gradle.serialization

import org.jetbrains.deft.proto.frontend.KotlinPart
import org.jetbrains.deft.proto.gradle.base.BindingPluginPart
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx

class SerializationPluginPart(ctx: PluginPartCtx) : BindingPluginPart by ctx {
    override val needToApply: Boolean by lazy {
        module.leafFragments.any { it.parts.find<KotlinPart>()?.serialization == "json" }
    }

    override fun applyBeforeEvaluate() {
        project.plugins.apply("org.jetbrains.kotlin.plugin.serialization")
    }
}