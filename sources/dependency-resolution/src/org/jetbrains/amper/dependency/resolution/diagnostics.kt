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
    val ContentLengthMismatch = DiagnosticDescriptor("content.length.mismatch", Severity.ERROR)
    val HashesMismatch = DiagnosticDescriptor("hashes.mismatch", Severity.ERROR)
    val SuccessfulDownload = DiagnosticDescriptor("successful.download", Severity.INFO)
    val SuccessfulLocalResolution = DiagnosticDescriptor("successful.local.resolution", Severity.INFO)
    val UnableToDownloadChecksums = DiagnosticDescriptor("unable.to.download.checksums", Severity.ERROR)
    val UnableToDownloadFile = DiagnosticDescriptor("unable.to.download.file", Severity.ERROR)
    val UnableToReachURL = DiagnosticDescriptor("unable.to.reach.url", Severity.ERROR)
    val UnableToResolveChecksums = DiagnosticDescriptor("unable.to.resolve.checksums", Severity.ERROR)
    val UnableToSaveDownloadedFile = DiagnosticDescriptor("unable.to.save.downloaded.file", Severity.ERROR)
    val UnexpectedErrorOnDownload = DiagnosticDescriptor("unexpected.error.on.download", Severity.ERROR)
}
