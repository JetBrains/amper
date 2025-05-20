/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

val AomModelDiagnosticFactories: List<AomModelDiagnosticFactory> = listOf(
    InconsistentComposeVersion,
    ModuleDependencyLoopFactory,
)

val AomSingleModuleDiagnosticFactories: List<AomSingleModuleDiagnosticFactory> = listOf(
    AndroidVersionShouldBeAtLeastMinSdkFactory,
    ComposeVersionWithDisabledCompose,
    IncorrectSettingsLocation,
    KeystoreMustExistFactory,
    KeystorePropertiesDoesNotContainKeyFactory,
    MandatoryFieldInPropertiesFileMustBePresentFactory,
    ModuleDependencyDoesntHaveNeededPlatformsFactory,
    SerializationVersionWithDisabledSerialization,
    SigningEnabledWithoutPropertiesFileFactory,
    UselessSettingValue,
    GradleSpecificUnsupportedFactory,
)
