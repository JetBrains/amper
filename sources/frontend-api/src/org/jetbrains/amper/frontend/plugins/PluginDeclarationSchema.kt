/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.StringSemantics
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.types.SchemaType.StringType.Semantics

/**
 * This schema is used in `module.yaml` files with project type `jvm/amper-plugin`.
 */
@SchemaDoc("Current plugin manifest information")
class PluginDeclarationSchema : SchemaNode() {
    @SchemaDoc("Plugin id that is going to be used to refer to the plugin in the configuration files. " +
            "Module name is used by default.")
    val id by value<TraceableString>() // Defaults to the module name, is set on the tree level later.

    @SchemaDoc("Plugin description. " +
            "Can be used by tooling to provide documentation on plugin references in configuration files.")
    val description by nullableValue<String>()

    @SchemaDoc("Fully qualified name of the @Configurable-annotated interface to be used as plugin configuration. " +
            "This interface can't come from a dependency, it must be declared in the source directory.")
    @StringSemantics(Semantics.PluginSettingsClass)
    val settingsClass by nullableValue<TraceableString>()
}
