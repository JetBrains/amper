/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.customTaskSchema

import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import java.nio.file.Path

class CustomTaskNode : SchemaNode() {
    var type by value<CustomTaskType>()

    @SchemaDoc("Run code from this module as task")
    var module by value<Path>()

    @SchemaDoc("Pass any JVM runtime option to the task")
    var jvmArguments by nullableValue<List<String>>()

    @SchemaDoc("Pass arguments to main function (entry point)")
    var programArguments by nullableValue<List<String>>()

    @SchemaDoc("Pass arguments to main function (entry point)")
    var environmentVariables by nullableValue<Map<String, String>>()

    @SchemaDoc("Explicitly depend on another task")
    var dependsOn by nullableValue<List<String>>()

    @SchemaDoc("Add task output to module sources")
    var addTaskOutputToSourceSet by nullableValue<List<AddTaskOutputToSourceSetNode>>()

    @SchemaDoc("Artifacts to publish from task output")
    var publishArtifact by nullableValue<List<PublishArtifactNode>>()
}
