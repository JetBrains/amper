/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.dependency.resolution.diagnostics.SimpleMessage

class SimpleDiagnosticDescriptor(val id: String, val defaultSeverity: Severity)

fun SimpleDiagnosticDescriptor.asMessage(
    vararg messageArgs: Any?,
    extra: String? = null,
    exception: Throwable? = null,
    overrideSeverity: Severity? = null,
    childMessages: List<Message> = emptyList(),
): SimpleMessage = SimpleMessage(
    text = DependencyResolutionBundle.message(id, *messageArgs),
    extra = extra ?: "",
    severity = overrideSeverity ?: defaultSeverity,
    throwable = exception,
    childMessages = childMessages,
    id = id,
)

object DependencyResolutionDiagnostics {
    val BomVariantNotFound = SimpleDiagnosticDescriptor("bom.variant.not.found", Severity.ERROR)
    val ContentLengthMismatch = SimpleDiagnosticDescriptor("content.length.mismatch", Severity.ERROR)
    val FailedRepackagingKMPLibrary = SimpleDiagnosticDescriptor("failed.repackaging.kmp.library", Severity.ERROR)
    val HashesMismatch = SimpleDiagnosticDescriptor("hashes.mismatch", Severity.ERROR)
    val KotlinMetadataHashNotResolved = SimpleDiagnosticDescriptor("kotlin.metadata.hash.not.resolved", Severity.ERROR)
    val KotlinMetadataMissing = SimpleDiagnosticDescriptor("kotlin.metadata.missing", Severity.ERROR)
    val KotlinMetadataNotResolved = SimpleDiagnosticDescriptor("kotlin.metadata.not.resolved", Severity.ERROR)
    val KotlinProjectStructureMetadataMissing = SimpleDiagnosticDescriptor("kotlin.project.structure.metadata.missing", Severity.ERROR)
    val ModuleFileNotDownloaded = SimpleDiagnosticDescriptor("module.file.not.downloaded", Severity.ERROR)
    val MoreThanOneVariant = SimpleDiagnosticDescriptor("more.than.one.variant", Severity.WARNING)
    val MoreThanOneVariantWithoutMetadata = SimpleDiagnosticDescriptor("more.than.one.variant.without.metadata", Severity.ERROR)
    val NoVariantForPlatform = SimpleDiagnosticDescriptor("no.variant.for.platform", Severity.ERROR)
    val PomWasFoundButMetadataIsMissing = SimpleDiagnosticDescriptor("pom.was.found.but.metadata.is.missing", Severity.WARNING)
    val PomWasNotFound = SimpleDiagnosticDescriptor("pom.was.not.found", Severity.WARNING)
    val ProjectHasMoreThanTenAncestors = SimpleDiagnosticDescriptor("project.has.more.than.ten.ancestors", Severity.WARNING)
    val SuccessfulDownload = SimpleDiagnosticDescriptor("successful.download", Severity.INFO)
    val SuccessfulLocalResolution = SimpleDiagnosticDescriptor("successful.local.resolution", Severity.INFO)
    val UnableToDetermineDependencyVersion = SimpleDiagnosticDescriptor("unable.to.determine.dependency.version", Severity.ERROR)
    val UnableToDetermineDependencyVersionForKotlinLibrary = SimpleDiagnosticDescriptor("unable.to.determine.dependency.version.for.kotlin.library", Severity.ERROR)
    val UnableToDownloadChecksums = SimpleDiagnosticDescriptor("unable.to.download.checksums", Severity.ERROR)
    val UnableToDownloadFile = SimpleDiagnosticDescriptor("unable.to.download.file", Severity.ERROR)
    val UnableToParseMetadata = SimpleDiagnosticDescriptor("unable.to.parse.metadata", Severity.ERROR)
    val UnableToParsePom = SimpleDiagnosticDescriptor("unable.to.parse.pom", Severity.ERROR)
    val UnableToReachURL = SimpleDiagnosticDescriptor("unable.to.reach.url", Severity.ERROR)
    val UnableToResolveChecksums = SimpleDiagnosticDescriptor("unable.to.resolve.checksums", Severity.ERROR)
    val UnableToResolveDependency = SimpleDiagnosticDescriptor("unable.to.resolve.dependency", Severity.ERROR)
    val UnableToSaveDownloadedFile = SimpleDiagnosticDescriptor("unable.to.save.downloaded.file", Severity.ERROR)
    val UnexpectedErrorOnDownload = SimpleDiagnosticDescriptor("unexpected.error.on.download", Severity.ERROR)
    val UnexpectedDependencyFormat = SimpleDiagnosticDescriptor("unexpected.dependency.format", Severity.ERROR)
    val UnspecifiedDependencyVersion = SimpleDiagnosticDescriptor("unspecified.dependency.version", Severity.ERROR)
}
