/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.api.HiddenFromCompletion
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableString

class Project : SchemaNode() {

    @SchemaDoc(
        "The relative paths or glob patterns defining the modules to include in this Amper project.\n\n" +
                "The module at the root of the project is always included by default and doesn't need to be listed.\n\n" +
                "Relative paths should be paths to the root directory of a module, directly containing the module file.\n  " +
                "For example, `./libs/util` will include the module defined by `./libs/util/module.yaml`.\n\n" +
                "Glob patterns can be used to match multiple module directories in one expression. " +
                "Only directories that actually contain a module file will be taken into account.\n  " +
                "For example, `./libs/*` will include the modules defined by `./libs/foo/module.yaml` and " +
                "`./libs/bar/module.yaml` (if these module files exist)."
    )
    var modules by value<List<TraceableString>>(default = emptyList())

    @SchemaDoc("The list of plugin dependencies to make *available* in the project modules. " +
            "Each plugin from this list can then be enabled and configured on the per-module basis using the " +
            "`plugins: { <plugin-id>: { enabled: true } }` configuration in the corresponding `module.yaml`s." +
            "Currently, only dependencies on local plugin modules are supported here.")
    var plugins by value<List<UnscopedModuleDependency>>(default = emptyList())

    @HiddenFromCompletion
    var mavenPlugins by value<List<UnscopedExternalMavenDependency>>(default = emptyList())
}
