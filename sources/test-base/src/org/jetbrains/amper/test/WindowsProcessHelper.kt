/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinError
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import org.slf4j.LoggerFactory
import java.io.File

/**
 * A utility class for finding and killing processes that hold locks on files in Windows.
 */
object WindowsProcessHelper {

    private val logger = LoggerFactory.getLogger(WindowsProcessHelper::class.java)

    // Constants for the Windows Restart Manager API
    private const val CCH_RM_SESSION_KEY = 32
    private const val CCH_RM_MAX_APP_NAME = 255
    private const val CCH_RM_MAX_SVC_NAME = 63

    // Structure definitions required for the Windows Restart Manager API
    @Structure.FieldOrder("dwProcessId", "ProcessStartTime")
    class RM_UNIQUE_PROCESS : Structure {
        @JvmField
        var dwProcessId = WinDef.DWORD(0)
        @JvmField
        var ProcessStartTime = FILETIME()

        constructor() : super()
        constructor(p: Pointer?) : super(p)
    }

    // FILETIME structure for JNA
    @Structure.FieldOrder("dwLowDateTime", "dwHighDateTime")
    class FILETIME : Structure {
        @JvmField
        var dwLowDateTime = 0
        @JvmField
        var dwHighDateTime = 0

        constructor() : super()
        constructor(p: Pointer?) : super(p)
    }

    @Structure.FieldOrder(
        "Process", "strAppName", "strServiceShortName",
        "ApplicationType", "AppStatus", "TSSessionId", "bRestartable"
    )
    class RM_PROCESS_INFO : Structure {
        @JvmField
        var Process = RM_UNIQUE_PROCESS()
        @JvmField
        var strAppName = CharArray(CCH_RM_MAX_APP_NAME + 1)
        @JvmField
        var strServiceShortName = CharArray(CCH_RM_MAX_SVC_NAME + 1)
        @JvmField
        var ApplicationType = WinDef.DWORD(0)
        @JvmField
        var AppStatus = WinDef.DWORD(0)
        @JvmField
        var TSSessionId = WinDef.DWORD(0)
        @JvmField
        var bRestartable = false

        constructor() : super()
        constructor(p: Pointer?) : super(p)

        override fun toString(): String {
            return "RM_PROCESS_INFO{" +
                    "pid=" + Process.dwProcessId.toInt() +
                    ", appName=" + String(strAppName).trim { it <= ' ' } +
                    '}'
        }
    }

    // Interface definition for the Windows Restart Manager API
    private interface RmLibrary : StdCallLibrary {
        fun RmStartSession(pSessionHandle: IntByReference, dwSessionFlags: Int, strSessionKey: CharArray): Int
        fun RmEndSession(dwSessionHandle: Int): Int
        fun RmRegisterResources(
            dwSessionHandle: Int,
            nFiles: Int,
            rgsFilenames: Array<String>?,
            nApplications: Int,
            rgApplications: Array<RM_UNIQUE_PROCESS>?,
            nServices: Int,
            rgsServiceNames: Array<String>?
        ): Int

        fun RmGetList(
            dwSessionHandle: Int,
            pnProcInfoNeeded: IntByReference,
            pnProcInfo: IntByReference,
            rgAffectedApps: Array<RM_PROCESS_INFO>?,
            lpdwRebootReasons: IntByReference?
        ): Int
    }

    private val kernel32 = Native.load("kernel32", Kernel32::class.java)
    private val rstrtmgr = Native.load("rstrtmgr", RmLibrary::class.java, W32APIOptions.DEFAULT_OPTIONS)

    fun findLockingProcessIds(filePath: String): List<Int> {
        val file = File(filePath)
        if (!file.exists()) {
            return emptyList()
        }

        val absolutePath = file.absolutePath

        // Generate a unique session key
        val sessionKey = CharArray(CCH_RM_SESSION_KEY + 1)
        val sessionHandle = IntByReference()

        try {
            // Start the restart manager session
            var result = rstrtmgr.RmStartSession(sessionHandle, 0, sessionKey)
            if (result != WinError.ERROR_SUCCESS) {
                error("Failed to start restart manager session: Error code $result")
            }

            // Register the file with the restart manager
            val resources = arrayOf(absolutePath)
            result = rstrtmgr.RmRegisterResources(
                sessionHandle.value,
                resources.size,
                resources,
                0,
                null,
                0,
                null
            )
            if (result != WinError.ERROR_SUCCESS) {
                error("Failed to register resources: Error code $result")
            }

            // Get the list of processes
            val pnProcInfoNeeded = IntByReference(0)
            val pnProcInfo = IntByReference(0)

            // First call to get required array size
            result = rstrtmgr.RmGetList(
                sessionHandle.value,
                pnProcInfoNeeded,
                pnProcInfo,
                null,
                IntByReference(0)
            )

            if (result == WinError.ERROR_MORE_DATA) {
                val neededSize = pnProcInfoNeeded.value

                // Check if we actually have processes to get information about
                if (neededSize <= 0) {
                    return emptyList()
                }

                // Create array with the needed size (make sure it's at least 1)
                val rgAffectedApps = Array(neededSize) { RM_PROCESS_INFO() }

                pnProcInfo.value = neededSize

                result = rstrtmgr.RmGetList(
                    sessionHandle.value,
                    pnProcInfoNeeded,
                    pnProcInfo,
                    rgAffectedApps,
                    IntByReference(0)
                )

                if (result == WinError.ERROR_SUCCESS) {
                    return rgAffectedApps.take(pnProcInfo.value).map { it.Process.dwProcessId.toInt() }
                }
            } else if (result == WinError.ERROR_SUCCESS) {
                // No processes locking the file
                return emptyList()
            }

            // Failed to get process list for reasons other than needing more data
            error("Failed to get process list: Error code $result")
        } catch (e: RuntimeException) {
            // Log the exception but still allow the process to continue
            logger.warn("Failed to check for locking processes: {}", e.message)
            return emptyList()
        } finally {
            try {
                // End the restart manager session
                rstrtmgr.RmEndSession(sessionHandle.value)
            } catch (e: Exception) {
                // Ignore exceptions when closing the session
                logger.warn("Failed to close restart manager session: {}", e.message)
            }
        }
    }

    fun killLockingProcesses(filePath: String): Int {
        val processIds = try {
            findLockingProcessIds(filePath)
        } catch (e: Exception) {
            // If finding processes fails, we still want to return 0 rather than failing completely
            logger.warn("Failed to find locking processes: {}", e.message)
            emptyList()
        }

        if (processIds.isEmpty()) {
            return 0
        }

        var killedCount = 0

        processIds.forEach { pid ->
            val processHandle = kernel32.OpenProcess(
                WinNT.PROCESS_TERMINATE,
                false,
                pid
            )

            if (processHandle != null && processHandle != WinNT.INVALID_HANDLE_VALUE) {
                try {
                    val success = kernel32.TerminateProcess(processHandle, 1)
                    if (success) {
                        killedCount++
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to terminate process $pid: {}", e.message)
                } finally {
                    kernel32.CloseHandle(processHandle)
                }
            }
        }

        return killedCount
    }

    fun unlockFile(filePath: String, timeout: Long = 5000): Boolean {
        logger.info("Unlocking file $filePath")
        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < timeout) {
            try {
                val count = killLockingProcesses(filePath)

                if (count == 0) {
                    // No more locking processes or the file is already unlocked
                    return true
                }

                // Wait a little before trying again
                Thread.sleep(100)
            } catch (e: Exception) {
                logger.warn("Exception while unlocking a file: {}", e.message)
                // Wait a little before retrying
                Thread.sleep(100)
            }
        }

        // Final check
        return try {
            findLockingProcessIds(filePath).isEmpty()
        } catch (e: Exception) {
            // If we can't determine if there are locking processes, assume it's unlocked
            logger.warn("Failed to check for locking processes in final check: {}", e.message)
            true
        }
    }
}
