/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.IgnoreForSchema
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.plugins.schema.model.PluginData
import java.nio.file.Path

class PluginYamlRoot : SchemaNode() {
    var tasks by value<Map<String, Task>>(default = emptyMap())

    companion object {
        /**
         * Reference-only property name
         */
        const val MODULE = "module"

        /**
         * Reference-only plugin settings property name
         */
        const val PLUGIN_SETTINGS = "pluginSettings"
    }
}

class TaskAction(
    @IgnoreForSchema val taskInfo: PluginData.TaskInfo,
) : SchemaNode() {
    operator fun get(key: String) = valueHolders[key]?.value
}

class Task : SchemaNode() {
    var dependsOnSideEffectsOf by value<List<String>>(default = emptyList())
    var action by value<TaskAction>()
    var markOutputsAs by value<List<MarkOutputAs>>(default = emptyList())

    companion object {
        /**
         * Reference-only property name
         */
        const val TASK_OUTPUT_DIR = "taskOutputDir"
    }
}

class MarkOutputAs : SchemaNode() {
    var path by value<Path>()
    var kind by value<GeneratedPathKind>()
    val fragment: FragmentDescriptor by nested()
}

class FragmentDescriptor : SchemaNode() {
    @Shorthand
    var modifier by value(default = "")
    var isTest by value(default = false)
}

enum class GeneratedPathKind(
    override val schemaValue: String,
) : SchemaEnum {
    KotlinSources("kotlin-sources"),
    JavaSources("java-sources"),
    JvmResources("jvm-resources"),
    ;

    override val outdated: Boolean
        get() = false
}