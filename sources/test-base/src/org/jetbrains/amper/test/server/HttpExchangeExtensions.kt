/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.server

import com.sun.net.httpserver.HttpExchange
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

internal fun HttpExchange.respondRedirect(newUrl: String) {
    responseHeaders.add("Location", newUrl)
    sendResponse(code = 302)
}

internal fun HttpExchange.respondInvalidRequest(message: String) {
    sendResponse(code = 400, body = message)
}

internal fun HttpExchange.respondInvalidMethod(supportedMethods: List<String>) {
    responseHeaders.add("Allow", supportedMethods.joinToString(", "))
    sendResponse(code = 405)
}

internal fun HttpExchange.respondInternalServerError(
    message: String = "Internal error in the test server",
    cause: Throwable? = null,
) {
    sendResponse(code = 500, body = TestHttpServerException(message, cause).stackTraceToString())
}

private class TestHttpServerException(message: String, cause: Throwable?) : Exception(message, cause)

private fun HttpExchange.sendResponse(code: Int, body: String? = null) {
    if (body == null) {
        sendResponseHeaders(code, -1)
        responseBody.close()
        return
    }
    val bytes = body.encodeToByteArray()
    sendResponseHeaders(code, bytes.size.toLong())
    responseBody.buffered().use { it.write(bytes) }
}

internal fun HttpExchange.respondWithLocalFile(filePath: Path) {
    if (!filePath.isRegularFile()) {
        sendResponseHeaders(404, -1)
        responseBody.close()
        return
    }
    sendResponseHeaders(200, filePath.fileSize())
    responseBody.writeFileContents(filePath)
}

internal fun OutputStream.writeFileContents(cachedFile: Path) {
    cachedFile.inputStream().use { input -> input.copyTo(this) }
    flush()
    close()
}
