package org.example

import org.example.api.Model
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware

var Gradle.knownModel: Model?
    get() = (this as ExtensionAware).extensions.extraProperties["org.example.knownModel"] as? Model
    set(value) {
        (this as ExtensionAware).extensions.extraProperties["org.example.knownModel"] = value
    }

@Suppress("UNCHECKED_CAST")
var Gradle.pathToModuleId: Map<String, String>?
    get() = (this as ExtensionAware).extensions.extraProperties["org.example.pathToModuleId"] as? Map<String, String>
    set(value) {
        (this as ExtensionAware).extensions.extraProperties["org.example.pathToModuleId"] = value
    }

@Suppress("UNCHECKED_CAST")
var Gradle.moduleIdToPath: Map<String, String>?
    get() = (this as ExtensionAware).extensions.extraProperties["org.example.moduleIdToPath"] as? Map<String, String>
    set(value) {
        (this as ExtensionAware).extensions.extraProperties["org.example.moduleIdToPath"] = value
    }