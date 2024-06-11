/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.get
import org.jetbrains.amper.core.messages.CollectingProblemReporter
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.messages.renderMessage
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelInit
import org.slf4j.LoggerFactory

@Suppress("unused") // Is passed via implementationClass option when declaring a plugin in the Gradle script.
class BindingSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val rootPath = settings.rootDir.toPath().toAbsolutePath()
        with(SLF4JProblemReporterContext()) {
            val model = ModelInit.getModel(rootPath)
            if (model is Result.Failure<Model> || problemReporter.hasFatal) {
                throw GradleException(problemReporter.getGradleError())
            }

            // Use [ModelWrapper] to cache and preserve links on [PotatoModule].
            val modelWrapper = ModelWrapper(model.get())
            SettingsPluginRun(settings, modelWrapper).run()
        }
    }
}
