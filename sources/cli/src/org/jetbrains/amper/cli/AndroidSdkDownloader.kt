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
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.downloader.httpClient
import org.jetbrains.amper.downloader.writeChannel
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.createParentDirectories

object NoopSettingsController : SettingsController {
    override fun getForceHttp(): Boolean = false

    override fun setForceHttp(force: Boolean) {}

    override fun getDisableSdkPatches(): Boolean = false

    override fun setDisableSdkPatches(disable: Boolean) {}

    override fun getChannel(): Channel = Channel.create(0)
}

class KtorDownloader(private val androidSdkPath: Path) : Downloader {
    override fun downloadAndStream(url: URL, indicator: ProgressIndicator): InputStream {
        return runBlocking {
            val response = httpClient.get(url) {
                onDownload { bytesSentTotal, contentLength ->
                    indicator.fraction = (bytesSentTotal.toDouble() / contentLength.toDouble())
                }
            }
            response.bodyAsChannel().toInputStream()
        }
    }

    override fun downloadFully(url: URL, indicator: ProgressIndicator): Path {
        return runBlocking {
            val target = androidSdkPath.resolve("target")
            val response = httpClient.get(url) {
                onDownload { bytesSentTotal, contentLength ->
                    indicator.fraction = (bytesSentTotal.toDouble() / contentLength.toDouble())
                }
            }
            response.bodyAsChannel().copyAndClose(writeChannel(target))
            target
        }
    }

    override fun downloadFully(url: URL, target: Path, checksum: Checksum, indicator: ProgressIndicator) {
        target.createParentDirectories()
        return runBlocking {
            val response = httpClient.get(url) {
                onDownload { bytesSentTotal, contentLength ->
                    indicator.fraction = (bytesSentTotal.toDouble() / contentLength.toDouble())
                }
            }
            response.bodyAsChannel().copyAndClose(writeChannel(target))
        }
    }
}

fun downloadAndExtractAndroidPlatform(platformCode: String): Path {
    val userHome = System.getProperty("user.home") ?: error("User home must not be null")
    val androidSdkPath = Path.of(userHome).resolve(".android-sdk")
    val sdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton, androidSdkPath)
    val consoleProgressIndicator = ConsoleProgressIndicator()
    val repoManager = sdkHandler.getSdkManager(consoleProgressIndicator)
    val downloader = KtorDownloader(androidSdkPath)
    val localPackage: LocalPackage? = repoManager.packages.localPackages["platforms;$platformCode"]
    return localPackage?.location ?: run {
        repoManager.loadSynchronously(0, consoleProgressIndicator, downloader, NoopSettingsController)
        val remotePackage: RemotePackage? = repoManager.packages.remotePackages["platforms;$platformCode"]
        val installer = BasicInstallerFactory().createInstaller(remotePackage, repoManager, downloader)
        installer.prepare(consoleProgressIndicator)
        installer.complete(consoleProgressIndicator)
        val installDir: Path? = remotePackage?.getInstallDir(repoManager, consoleProgressIndicator)
        installDir ?: error("Install dir of package $remotePackage is missing")
    }
}