/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.plugins.Configurable
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

@Configurable
interface ThirdPartyComponentVersions {
    val busyboxW32Version: String
}

@TaskAction
fun provisionThirdPartyComponents(
    versions: ThirdPartyComponentVersions,
    @Input ourLicenseFile: Path,
    @Output stagingDir: Path,
) {
    stagingDir.deleteRecursively()
    stagingDir.createDirectories()

    // FIXME(inhatkevich, AMPER-5156): Solve the issue with provisioning the external artifacts, restore this code
    //val busyboxBinaries = listOf(
    //    DownloadableResource(
    //        url = "$BUSYBOX_W32_DOWNLOAD_ROOT/busybox-w32-${versions.busyboxW32Version}.tgz",
    //        pathInsideTheDistribution = Path("third-party-sources/busybox-w32-${versions.busyboxW32Version}.tgz"),
    //        verifySignature = false,  // Sources are not signed!
    //    ),
    //    DownloadableResource(
    //        url = "$BUSYBOX_W32_DOWNLOAD_ROOT/busybox-w64a-${versions.busyboxW32Version}.exe",
    //        pathInsideTheDistribution = Path("bin/busybox64a.exe"),
    //    ),
    //    DownloadableResource(
    //        url = "$BUSYBOX_W32_DOWNLOAD_ROOT/busybox-w64u-${versions.busyboxW32Version}.exe",
    //        pathInsideTheDistribution = Path("bin/busybox64u.exe"),
    //    ),
    //)

    // FIXME(inhatkevich, AMPER-5156): Remove this workaround
    // We just copy the busybox binaries and sources from the host Amper distribution
    val currentAmperDistribution = checkNotNull(System.getenv("AMPER_DISTRIBUTION_DIR")) {
        "AMPER_DISTRIBUTION_DIR environment variable is not set"
    }.let(::Path)
    val sourcesArchiveName = "busybox-w32-${versions.busyboxW32Version}.tgz"
    currentAmperDistribution.resolve("third-party-sources").resolve(sourcesArchiveName)
        .copyTo(stagingDir.resolve("third-party-sources").createDirectories().resolve(sourcesArchiveName))
    currentAmperDistribution.resolve("bin").let { bin ->
        val targetBin = stagingDir.resolve("bin").createDirectories()
        for (binaryName in arrayOf("busybox64a.exe", "busybox64u.exe")) {
            bin.resolve(binaryName).copyTo(targetBin.resolve(binaryName))
        }
    }

    val builtinResources = listOf(
        BuiltinResource(
            resourceName = "NOTICE.txt",
            textTransform = {
                it.replace(BUSYBOX_W32_VERSION_PLACEHOLDER, versions.busyboxW32Version)
            },
        ),
        BuiltinResource(
            resourceName = "GPL-2.0.txt",
            pathInsideTheDistribution = Path("licenses"),
        ),
    )

    // FIXME(inhatkevich, AMPER-5156): Restore this code
    // busyboxBinaries.forEach { it.download(stagingDir) }
    builtinResources.forEach { it.stage(stagingDir) }

    ourLicenseFile.copyTo(
        target = (stagingDir / "licenses" / "Apache-2.0.txt")
    )
}

private const val BUSYBOX_W32_DOWNLOAD_ROOT = "https://frippery.org/files/busybox"
private const val BUSYBOX_W32_VERSION_PLACEHOLDER = "@BUSYBOX_W32_VERSION@"

private data class DownloadableResource(
    val url: String,
    val pathInsideTheDistribution: Path,
    val verifySignature: Boolean = true,
) {
    init {
        require(!pathInsideTheDistribution.isAbsolute)
    }

    fun download(stagingDir: Path) {
        val binaryFile = stagingDir / pathInsideTheDistribution
        binaryFile.createParentDirectories()
        downloadFile(url, binaryFile)

        if (verifySignature) {
            val signatureFile = createTempFile()
            try {
                downloadFile("${url}.sig", signatureFile)
                verifyGpgSignature(binaryFile, signatureFile, BUSYBOX_W32_GPG_PUBLIC_KEY)
            } finally {
                signatureFile.deleteIfExists()
            }
        }
    }
}

private data class BuiltinResource(
    /** java resource name */
    val resourceName: String,
    /** prefix path inside the distribution. [resourceName] is appended to this. */
    val pathInsideTheDistribution: Path = Path("."),
    /** optional transform to apply to resource (read as UTF-8) */
    val textTransform: ((String) -> String)? = null,
) {
    init {
        require(!pathInsideTheDistribution.isAbsolute)
    }

    fun stage(stagingDir: Path) {
        val target = stagingDir / pathInsideTheDistribution / resourceName
        val resource = javaClass.classLoader.getResourceAsStream(resourceName)
            ?: error("Resource not found: $resourceName")
        target.createParentDirectories()
        if (textTransform != null) {
            // Treat as text
            val transformed = textTransform(resource.readAllBytes().decodeToString())
            target.writeText(transformed)
        } else {
            // Copy binary
            resource.buffered().use { from ->
                target.outputStream(StandardOpenOption.CREATE_NEW).buffered().use { to ->
                    from.transferTo(to)
                }
            }
        }
    }
}

// GPG public key used to verify busybox-w32 binary signatures.
// From https://frippery.org/files/RPM-GPG-KEY-rmy-2
private val BUSYBOX_W32_GPG_PUBLIC_KEY = """
    -----BEGIN PGP PUBLIC KEY BLOCK-----
    
    mDMEaNEq0xYJKwYBBAHaRw8BAQdAFG+5ZSHKq6O0jVWMQDJXU98BLdQwbUkQsvlp
    GYJVnZK0L1JvbiBZb3JzdG9uIChSUE0gc2lnbmluZyBrZXkgMikgPHJteUBwb2Jv
    eC5jb20+iJMEExYKADsWIQS0PiRLkqgTicqqoXFpDhCgUT2oSwUCaNEq0wIbAwUL
    CQgHAgIiAgYVCgkICwIEFgIDAQIeBwIXgAAKCRBpDhCgUT2oS30DAQDnM0++iUFR
    t1UyAU27ARGkSiyLmUXOKdYOmAJzlUyrcwEAzKSf50M25Th/BOA1qR88zqKplFuO
    y1ofSOpqDBETuQK4OARo0SrTEgorBgEEAZdVAQUBAQdARvpUBtXo8ddVEszCma3r
    AxnKlDG1RhfovmOIMZY+FwUDAQgHiHgEGBYKACAWIQS0PiRLkqgTicqqoXFpDhCg
    UT2oSwUCaNEq0wIbDAAKCRBpDhCgUT2oS7G8AQDaPxhS5bYi3l39deyCyFxrWGa6
    yB2TCzKIMBKiv44TSQEAkc6ZzuwijSDtGl348RNUSKB4g8LaNzG6c/jk3YXIXwM=
    =A/33
    -----END PGP PUBLIC KEY BLOCK-----
""".trimIndent()
