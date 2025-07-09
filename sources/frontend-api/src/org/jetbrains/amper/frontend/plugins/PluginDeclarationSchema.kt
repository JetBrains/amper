/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode

/**
 * This schema is used in `module.yaml` files with project type `jvm/amper-plugin`.
 */
@SchemaDoc("Current plugin manifest information")
class PluginDeclarationSchema : SchemaNode() {
    @SchemaDoc("Plugin id that is going to be used to refer to the plugin in the configuration files")
    var id by value<String>()

    @SchemaDoc("Plugin description. " +
            "Can be used by tooling to provide documentation on plugin references in configuration files.")
    var description by nullableValue<String>()

    @SchemaDoc("Fully qualified name of the @Schema-annotated interface to be used as plugin configuration. " +
            "This interface can't come from a dependency, it must be declared in the source directory.")
    var schemaExtensionClassName by nullableValue<String>()
}
