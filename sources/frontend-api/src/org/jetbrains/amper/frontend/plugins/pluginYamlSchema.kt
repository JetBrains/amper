/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.IgnoreForSchema
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.plugins.schema.model.PluginData
import java.nio.file.Path

class PluginYamlRoot : SchemaNode() {
    @SchemaDoc("The tasks registered by this plugin. This is a map from task name to task registration data. " +
            "Task names are simple identifiers which are local to the plugin and are not required to be globally unique. " +
            "They are conventionally lowerCamelCase.")
    val tasks by value<Map<String, Task>>(default = emptyMap())

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
    @SchemaDoc("The task action definition. Requires a type specifier in the form of `!com.example.myTaskAction`, " +
            "where `com.example.myTaskAction` is the fully-qualified name of a `@TaskAction`-annotated " + 
            "function defined in plugin sources. " +
            "All the nested properties correspond to the chosen task action's parameters, and define the " +
            "values of the arguments.")
    val action by value<TaskAction>()

    @SchemaDoc("The list of additional semantics specifiers for the task outputs. " +
            "If a task's output (e.g. generated sources) should be contributed back to the build, " +
            "add the corresponding entry to this list.")
    val markOutputsAs by value<List<MarkOutputAs>>(default = emptyList())

    companion object {
        /**
         * Reference-only property name
         */
        const val TASK_OUTPUT_DIR = "taskOutputDir"
    }
}

class MarkOutputAs : SchemaNode() {
    @SchemaDoc("The path to one of the task outputs. " +
            "It has to match one of the @Output-marked paths of the task action properties, " +
            "or a path nested in a @Output-marked object.")
    val path by value<Path>()

    @SchemaDoc("The kind of files that reside at the given path, which determines how they're going to be used in the build.")
    val kind by value<GeneratedPathKind>()

    @SchemaDoc("A \"fragment\" descriptor defining the platform or main/test scope the generated files are " +
            "contributed to. " +
            "By default, the generated contents are associated with the main fragment in JVM-only modules, " + 
            "or with the most common production fragment in multiplatform modules. This means that the " +
            "generated files are visible to all fragments by default.")
    val fragment: FragmentDescriptor by nested()
}

class FragmentDescriptor : SchemaNode() {
    @Shorthand
    @SchemaDoc("The fragment qualifier without the `@` symbol (e.g. `jvm`, `ios`, etc.), or the empty " +
            "string for the fragment that doesn't have a qualifier (this is the main fragment in " +
            "JVM-only modules, or the most common fragment in multiplatform modules, which means that the " +
            "generated files are visible transitively in all fragments of the module). " +
            "Empty string by default.")
    var modifier by value(default = "")

    @SchemaDoc("`true` to select a test fragment, `false` by default")
    var isTest by value(default = false)
}

enum class GeneratedPathKind(
    override val schemaValue: String,
) : SchemaEnum {
    @SchemaDoc("Kotlin sources to be compiled together with the module")
    KotlinSources("kotlin-sources"),

    @SchemaDoc("Java sources to be compiled together with the module")
    JavaSources("java-sources"),

    @SchemaDoc("Resources to be bundled together with the module")
    JvmResources("jvm-resources"),
    ;

    override val outdated: Boolean
        get() = false
}