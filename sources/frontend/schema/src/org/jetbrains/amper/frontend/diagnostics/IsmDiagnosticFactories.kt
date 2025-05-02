/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

val IsmDiagnosticFactories: List<IsmDiagnosticFactory> = listOf(
    AndroidTooOldVersionFactory,
    LibShouldHavePlatforms,
    MavenLocalResolutionUnsupported,
    ProductPlatformIsUnsupported,
    ProductPlatformsShouldNotBeEmpty,
    UnknownQualifiers,
    TemplateNameWithoutPostfix,
    UnresolvedTemplate,
    AliasesDontUseUndeclaredPlatform,
    AliasesAreNotIntersectingWithNaturalHierarchy,
)