/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading.maven

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.annotations.Nls
import java.nio.file.InvalidPathException
import kotlin.io.path.Path

context(reporter: ProblemReporter)
internal fun validateAndReportMavenCoordinates(
    origin: PsiElement,
    coordinates: String,
): Boolean {
    if (' ' in coordinates) {
        reporter.reportMessage(MavenCoordinatesHaveSpace(origin, coordinates))
        return false
    }

    if ('\n' in coordinates || '\r' in coordinates) {
        reporter.reportMessage(MavenCoordinatesHaveLineBreak(origin, coordinates))
        return false
    }

    if ('/' in coordinates || '\\' in coordinates) {
        reporter.reportMessage(MavenCoordinatesHaveSlash(origin, coordinates))
        return false
    }

    val parts = coordinates.trim().split(":")

    if (parts.size < 2) {
        reporter.reportMessage(MavenCoordinatesHaveTooFewParts(origin, coordinates, parts.size))
        reportIfCoordinatesAreGradleLike(origin, coordinates)
        return false
    }

    if (parts.size > 4) {
        reporter.reportMessage(MavenCoordinatesHaveTooManyParts(origin, coordinates, parts.size))
        return false
    }

    parts.forEach { part ->
        try {
            // It throws InvalidPathException in case coordinates contain some restricted symbols.
            Path(part)
        } catch (e: InvalidPathException) {
            reporter.reportMessage(MavenCoordinatesShouldBuildValidPath(origin, coordinates, part, e))
            reportIfCoordinatesAreGradleLike(origin, coordinates)
            return false
        }

        if (part.endsWith(".")) {
            reporter.reportMessage(MavenCoordinatesHavePartEndingWithDot(origin, coordinates))
            return false
        }
    }

    val classifier = if (parts.size > 3) parts[3].trim() else null
    if (classifier != null) {
        reporter.reportMessage(MavenClassifiersAreNotSupported(origin, coordinates, classifier))
    }

    return true
}

context(reporter: ProblemReporter)
private fun reportIfCoordinatesAreGradleLike(
    origin: PsiElement,
    coordinates: String,
) {
    val probableGradleScope = GradleScope.parseGradleScope(coordinates)
    if (probableGradleScope != null) {
        val (gradleScope, trimmedCoordinates) = probableGradleScope
        reporter.reportMessage(
            DependencyCoordinatesInGradleFormat(
                element = origin,
                coordinates = coordinates,
                gradleScope = gradleScope,
                trimmedCoordinates = trimmedCoordinates
            )
        )
    }
}

abstract class MavenCoordinatesParsingProblem(
    level: Level = Level.Error,
) : PsiBuildProblem(
    level = level,
    type = BuildProblemType.Generic,
) {
    abstract val coordinates: String

    open val details: @Nls String? get() = null

    open val shortMessage: @Nls String get() = message

    val detailedMessage: @Nls String
        get() = if (details == null) message else "$message\n$details"
}