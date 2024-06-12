/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.customTaskSchema

import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode

@SchemaDoc("Add folder from ")
class AddTaskOutputToSourceSetNode: SchemaNode() {
    @SchemaDoc("Use this folder from task output to add to sources of current module")
    var taskOutputSubFolder by value<String>()

    @SchemaDoc("Whether add to test sources, defaults to adding to production sources")
    var addToTestSources by value(false)

    @SchemaDoc("Platform to add sources")
    var platform by value<String>()
}
