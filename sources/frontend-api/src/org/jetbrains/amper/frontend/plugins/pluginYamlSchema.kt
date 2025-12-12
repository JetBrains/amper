/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.CanBeReferenced
import org.jetbrains.amper.frontend.api.IgnoreForSchema
import org.jetbrains.amper.frontend.api.ReadOnly
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.frontend.plugins.generated.ShadowClasspath
import org.jetbrains.amper.frontend.plugins.generated.ShadowCompilationArtifact
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependencyLocal
import org.jetbrains.amper.frontend.plugins.generated.ShadowModuleSources
import org.jetbrains.amper.plugins.schema.model.PluginData
import java.nio.file.Path

class PluginYamlRoot : SchemaNode() {
    @SchemaDoc("The tasks registered by this plugin. This is a map from task name to task registration data. " +
            "Task names are simple identifiers which are local to the plugin and are not required to be globally unique. " +
            "They are conventionally lowerCamelCase.")
    val tasks by value<Map<String, Task>>(default = emptyMap())

    @ReadOnly
    @SchemaDoc("Data from the module the plugin is applied to")
    val module by value<ModuleDataForPlugin>()

    companion object {

        /**
         * Reference-only plugin settings property name.
         *
         * NOTE: Can't be expressed via the `@ReadOnly` property because its exact type is plugin-dependent.
         * If a plugin doesn't define the settings type, then this property is not present at all.
         */
        const val PLUGIN_SETTINGS = "pluginSettings"
    }
}

/**
 * NOTE: We do not mark all the properties here as [ReadOnly] because the [PluginYamlRoot.module] is already marked.
 * So all these properties also cannot be set by the user.
 */
class ModuleDataForPlugin : SchemaNode() {
    @CanBeReferenced
    @SchemaDoc("Name of the module")
    val name by value<String>()

    @CanBeReferenced
    @SchemaDoc("Module's root directory (where `module.yaml` resides)")
    val rootDir by value<Path>()

    @CanBeReferenced
    @SchemaDoc("Runtime classpath of the module (jvm, main)")
    val runtimeClasspath by value<ShadowClasspath>()

    @CanBeReferenced
    @SchemaDoc("Compilation classpath of the module (jvm, main)")
    val compileClasspath by value<ShadowClasspath>()

    @CanBeReferenced
    @SchemaDoc("Kotlin and Java source directories of the module (jvm, main)")
    val kotlinJavaSources by value<ShadowModuleSources>()

    @CanBeReferenced
    @SchemaDoc("Resource directories of the module (jvm, main)")
    val resources by value<ShadowModuleSources>()

    @CanBeReferenced
    @SchemaDoc("Compiled JAR for the module (jvm, main)")
    val jar by value<ShadowCompilationArtifact>()

    @CanBeReferenced
    @SchemaDoc("Dependency on the module itself")
    val self by value<ShadowDependencyLocal>()
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

    @ReadOnly
    @CanBeReferenced
    @SchemaDoc("Dedicated task directory under the build root")
    val taskOutputDir by value<Path>()
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
    val modifier by value(default = "")

    @SchemaDoc("`true` to select a test fragment, `false` by default")
    val isTest by value(default = false)
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