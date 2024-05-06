/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import org.slf4j.Logger

class LoggingProcessOutputListener(val logger: Logger): ProcessOutputListener {
    override fun onStdoutLine(line: String) {
        logger.info(line)
    }

    override fun onStderrLine(line: String) {
        logger.error(line)
    }
}