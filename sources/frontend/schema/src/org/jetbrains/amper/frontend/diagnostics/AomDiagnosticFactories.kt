/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

val AomModelDiagnosticFactories: List<AomModelDiagnosticFactory> = listOf(
    JvmReleaseLowerThanDependencies,
    ModuleDependencyLoopFactory,
    PublishingSettingsMissingInDependencies,
)

val AomSingleModuleDiagnosticFactories: List<AomSingleModuleDiagnosticFactory> = listOf(
    AndroidVersionShouldBeAtLeastMinSdkFactory,
    ComposeVersionWithDisabledCompose,
    JavaIncrementalCompilationRequiresJava16Factory,
    JdkDistributionRequiresLicenseFactory,
    JUnitRequiresHigherJdkVersionFactory,
    SerializationVersionWithDisabledSerialization,
    SigningEnabledWithoutPropertiesFileFactory,
    KeystorePropertiesDoesNotContainKeyFactory,
    MandatoryFieldInPropertiesFileMustBePresentFactory,
    KeystoreMustExistFactory,
    ModuleDependencyDoesntHaveNeededPlatformsFactory,
)
