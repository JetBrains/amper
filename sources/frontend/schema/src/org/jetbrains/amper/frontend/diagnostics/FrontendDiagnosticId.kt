/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.problems.reporting.DiagnosticId

/**
 * Diagnostics reported about module semantics (module and template files).
 */
enum class FrontendDiagnosticId : DiagnosticId {
    AliasExpandsToNothing,
    AliasInSinglePlatformModule,
    AliasIntersectsWithNaturalHierarchy,
    AliasIsEmpty,
    AliasUsesNonLeafPlatform,
    AliasUsesUndeclaredPlatform,
    AndroidVersionShouldBeAtLeastMinSdk,
    AndroidVersionTooOld,
    ComposeVersionWithoutCompose,
    CredentialsFileDoesNotExist,
    CredentialsFileDoesNotHaveKey,
    DependencyResolutionProblem,
    DependencyVersionIsOverridden,
    IncorrectSettingsSection,
    InvalidKotlinCompilerVersion,
    InvalidXmlForPlexusConfiguration,
    JavaIncrementalCompilationRequiresJava16,
    JdkDistributionRequiresLicense,
    JUnitRequiresHigherJdkVersion,
    JvmReleaseTooLowForDependency,
    KeystorePropertiesDoesNotContainKey,
    KeystoreFileDoesNotExist,
    KotlinCompilerVersionTooLow,
    MandatoryFieldInPropertiesFileMustBePresent,
    ModuleDependencyDoesntHaveNeededPlatforms,
    ModuleDependencyLoopProblem,
    ModuleDependencySelfProblem,
    NoCatalogValue,
    ProductNotDefined,
    ProductTypeDoesNotSupportPlatform,
    ProductTypeHasNoDefaultPlatforms,
    ProductPlatformsShouldNotBeEmpty,
    PublishingSettingsMissingInDependencies,
    SerializationVersionWithoutSerialization,
    SigningEnabledWithoutPropertiesFile,
    TemplateNameWithoutPostfix,
    UnknownProperty,
    UnknownQualifiers,
    UnresolvedModuleDeclaration,
    UnresolvedModuleDependency,
    UnresolvedTemplate,
    UnsupportedLayout,
    UselessSetting,
    VersionCannotBeEmpty,
}