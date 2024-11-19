/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics.rmi

import java.io.Serializable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.rmi.server.RMIClientSocketFactory
import java.rmi.server.RMIServerSocketFactory

/**
 * Forces RMI to connect via loopback address
 */
object LoopbackServerSocketFactory : RMIServerSocketFactory, Serializable {
    private fun readResolve(): Any = LoopbackServerSocketFactory

    override fun createServerSocket(port: Int): ServerSocket {
        return ServerSocket(port, 1, InetAddress.getLoopbackAddress())
    }
}

/**
 * Forces RMI to connect via loopback address
 */
object LoopbackClientSocketFactory : RMIClientSocketFactory, Serializable {
    private fun readResolve(): Any = LoopbackClientSocketFactory

    val hostName: String get() = InetAddress.getLoopbackAddress().hostName

    override fun createSocket(host: String?, port: Int): Socket {
        return Socket(hostName, port)
    }
}