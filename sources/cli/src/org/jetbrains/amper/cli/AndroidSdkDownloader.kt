/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.android.prefs.AndroidLocationsSingleton
import com.android.repository.api.Channel
import com.android.repository.api.Checksum
import com.android.repository.api.ConsoleProgressIndicator
import com.android.repository.api.Downloader
import com.android.repository.api.LocalPackage
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RemotePackage
import com.android.repository.api.SettingsController
import com.android.repository.impl.installer.BasicInstallerFactory
import com.android.sdklib.repository.AndroidSdkHandler
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.concurrency.withDoubleLock
import org.jetbrains.amper.downloader.httpClient
import java.io.InputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div

object NoopSettingsController : SettingsController {
    override fun getForceHttp(): Boolean = false

    override fun setForceHttp(force: Boolean) {}

    override fun getDisableSdkPatches(): Boolean = false

    override fun setDisableSdkPatches(disable: Boolean) {}

    override fun getChannel(): Channel = Channel.create(0)
}

class KtorDownloader(private val androidSdkPath: Path) : Downloader {
    override fun downloadAndStream(url: URL, indicator: ProgressIndicator): InputStream = runBlocking {
        httpResponse(url, indicator)
    }

    override fun downloadFully(url: URL, indicator: ProgressIndicator): Path =
        download(url, indicator, androidSdkPath.resolve("target"))

    override fun downloadFully(url: URL, target: Path, checksum: Checksum, indicator: ProgressIndicator) {
        download(url, indicator, target)
    }

    private fun download(url: URL, indicator: ProgressIndicator, target: Path): Path {
        target.createParentDirectories()
        val outputChannel = runBlocking {
            Channels.newChannel(httpResponse(url, indicator))
        }

        val inputChannel: FileChannel = FileChannel.open(
            target,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )

        inputChannel.use { ic ->
            outputChannel.use { oc ->
                ic.transferFrom(oc, 0, Long.MAX_VALUE)
            }
        }
        return target
    }

    private suspend fun httpResponse(url: URL, indicator: ProgressIndicator): InputStream = httpClient.get(url) {
        onDownload { bytesSentTotal, contentLength ->
            indicator.fraction = (bytesSentTotal.toDouble() / contentLength.toDouble())
        }
    }.body<InputStream>()
}

suspend fun downloadAndExtractAndroidPlatform(packageName: String, androidSdkPath: Path): Path =
    withDoubleLock(androidSdkPath.hashCode(), androidSdkPath / "lock") {
        val sdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton, androidSdkPath)
        val consoleProgressIndicator = ConsoleProgressIndicator()
        val repoManager = sdkHandler.getSdkManager(consoleProgressIndicator)
        val downloader = KtorDownloader(androidSdkPath)
        val localPackage: LocalPackage? = repoManager.packages.localPackages[packageName]
        localPackage?.location ?: run {
            repoManager.loadSynchronously(0, consoleProgressIndicator, downloader, NoopSettingsController)
            val remotePackage: RemotePackage? = repoManager.packages.remotePackages[packageName]
            remotePackage?.license?.setAccepted(repoManager.localPath)
            val installer = BasicInstallerFactory().createInstaller(remotePackage, repoManager, downloader)
            installer.prepare(consoleProgressIndicator)
            installer.complete(consoleProgressIndicator)
            val installDir: Path? = remotePackage?.getInstallDir(repoManager, consoleProgressIndicator)
            installDir ?: error("Install dir of package $remotePackage is missing")
        }
    }
