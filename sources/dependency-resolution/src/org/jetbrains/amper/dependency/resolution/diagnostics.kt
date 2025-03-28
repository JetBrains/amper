/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

class DiagnosticDescriptor(val id: String, val defaultSeverity: Severity)

fun DiagnosticDescriptor.asMessage(
    vararg messageArgs: Any?,
    extra: String? = null,
    exception: Throwable? = null,
    overrideSeverity: Severity? = null,
    suppressedMessages: List<Message> = emptyList(),
): Message = Message(
    text = DependencyResolutionBundle.message(id, *messageArgs),
    extra = extra ?: "",
    severity = overrideSeverity ?: defaultSeverity,
    exception = exception,
    suppressedMessages = suppressedMessages,
    id = id,
)

object DependencyResolutionDiagnostics {
    val BomVariantNotFound = DiagnosticDescriptor("bom.variant.not.found", Severity.ERROR)
    val ContentLengthMismatch = DiagnosticDescriptor("content.length.mismatch", Severity.ERROR)
    val FailedRepackagingKMPLibrary = DiagnosticDescriptor("failed.repackaging.kmp.library", Severity.ERROR)
    val HashesMismatch = DiagnosticDescriptor("hashes.mismatch", Severity.ERROR)
    val KotlinMetadataHashNotResolved = DiagnosticDescriptor("kotlin.metadata.hash.not.resolved", Severity.ERROR)
    val KotlinMetadataMissing = DiagnosticDescriptor("kotlin.metadata.missing", Severity.ERROR)
    val KotlinMetadataNotResolved = DiagnosticDescriptor("kotlin.metadata.not.resolved", Severity.ERROR)
    val KotlinProjectStructureMetadataMissing = DiagnosticDescriptor("kotlin.project.structure.metadata.missing", Severity.ERROR)
    val ModuleFileNotDownloaded = DiagnosticDescriptor("module.file.not.downloaded", Severity.ERROR)
    val MoreThanOneVariant = DiagnosticDescriptor("more.than.one.variant", Severity.WARNING)
    val MoreThanOneVariantWithoutMetadata = DiagnosticDescriptor("more.than.one.variant.without.metadata", Severity.ERROR)
    val NoVariantForPlatform = DiagnosticDescriptor("no.variant.for.platform", Severity.ERROR)
    val PomWasFoundButMetadataIsMissing = DiagnosticDescriptor("pom.was.found.but.metadata.is.missing", Severity.WARNING)
    val PomWasNotFound = DiagnosticDescriptor("pom.was.not.found", Severity.WARNING)
    val ProjectHasMoreThanTenAncestors = DiagnosticDescriptor("project.has.more.than.ten.ancestors", Severity.WARNING)
    val SuccessfulDownload = DiagnosticDescriptor("successful.download", Severity.INFO)
    val SuccessfulLocalResolution = DiagnosticDescriptor("successful.local.resolution", Severity.INFO)
    val UnableToDetermineDependencyVersion = DiagnosticDescriptor("unable.to.determine.dependency.version", Severity.ERROR)
    val UnableToDetermineDependencyVersionForKotlinLibrary = DiagnosticDescriptor("unable.to.determine.dependency.version.for.kotlin.library", Severity.ERROR)
    val UnableToDownloadChecksums = DiagnosticDescriptor("unable.to.download.checksums", Severity.ERROR)
    val UnableToDownloadFile = DiagnosticDescriptor("unable.to.download.file", Severity.ERROR)
    val UnableToParseMetadata = DiagnosticDescriptor("unable.to.parse.metadata", Severity.ERROR)
    val UnableToParsePom = DiagnosticDescriptor("unable.to.parse.pom", Severity.ERROR)
    val UnableToReachURL = DiagnosticDescriptor("unable.to.reach.url", Severity.ERROR)
    val UnableToResolveChecksums = DiagnosticDescriptor("unable.to.resolve.checksums", Severity.ERROR)
    val UnableToResolveDependency = DiagnosticDescriptor("unable.to.resolve.dependency", Severity.ERROR)
    val UnableToSaveDownloadedFile = DiagnosticDescriptor("unable.to.save.downloaded.file", Severity.ERROR)
    val UnexpectedErrorOnDownload = DiagnosticDescriptor("unexpected.error.on.download", Severity.ERROR)
    val UnexpectedDependencyFormat = DiagnosticDescriptor("unexpected.dependency.format", Severity.ERROR)
}
