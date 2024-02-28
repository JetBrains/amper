/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.schema.JavaVersion
import kotlin.reflect.KProperty0

class JavaSourceShouldBeAtMostJvmTarget(
    @UsedInIdePlugin
    val javaSourceProp: KProperty0<JavaVersion?>,
    @UsedInIdePlugin
    val jvmTargetProp: KProperty0<JavaVersion>,
) : PsiBuildProblem(Level.Error) {
    override val element: PsiElement
        get() = javaSourceProp.extractPsiElement()

    override val buildProblemId: BuildProblemId = JavaSourceShouldBeAtMostJvmTargetFactory.diagnosticId
    override val message: String
        get() = SchemaBundle.message(
            messageKey = buildProblemId,
            sourceVersion?.schemaValue,
            jvmTargetProp.get().schemaValue
        )

    @UsedInIdePlugin
    val sourceVersion: JavaVersion?
        get() = javaSourceProp.get()
}

object JavaSourceShouldBeAtMostJvmTargetFactory : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "java.source.version.should.be.at.most.jvm.target"

    context(ProblemReporterContext) override fun PotatoModule.analyze() {
        /**
         * Here we save pairs of traces into the set to avoid reporting the same error twice when traversing
         * dependent fragments, which have those conflicting settings propagated deeper.
         */
        val reportedPlaces = mutableSetOf<Pair<Trace?, Trace?>>()
        fragments.forEach { fragment ->
            val settings = fragment.settings
            val javaSourceProp = settings.java::source
            val jvmTargetProp = settings.jvm::target
            val sourceVersion = javaSourceProp.get() ?: return@forEach
            if (sourceVersion > settings.jvm.target) {
                val pair = javaSourceProp.valueBase?.trace to jvmTargetProp.valueBase?.trace
                if (!reportedPlaces.add(pair)) return@forEach

                problemReporter.reportMessage(
                    JavaSourceShouldBeAtMostJvmTarget(
                        javaSourceProp = javaSourceProp,
                        jvmTargetProp = jvmTargetProp,
                    )
                )
            }
        }
    }
}