/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

val AomModelDiagnosticFactories: List<AomModelDiagnosticFactory> = listOf(
    InconsistentComposeVersion,
)

val AomSingleModuleDiagnosticFactories: List<AomSingleModuleDiagnosticFactory> = listOf(
    AndroidVersionShouldBeAtLeastMinSdkFactory,
    ComposeVersionWithDisabledCompose,
    SerializationVersionWithDisabledSerialization,
    UselessSettingValue,
    IncorrectSettingsLocation,
    SigningEnabledWithoutPropertiesFileFactory,
    KeystorePropertiesDoesNotContainKeyFactory,
    MandatoryFieldInPropertiesFileMustBePresentFactory,
    KeystoreMustExistFactory,
)
