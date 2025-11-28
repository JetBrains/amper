// Copyright (C) 2025 Zac Sweers
// SPDX-License-Identifier: Apache-2.0
package dev.zacsweers.metro.sample.weather

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import dev.zacsweers.metro.createGraph
import kotlin.system.exitProcess

class WeatherCli : SuspendingCliktCommand() {
  private val location by option("--location").default("New York")

  override suspend fun run() {
    val app = createGraph<JvmAppGraph>().app
    app(location) { message, isError -> echo(message, err = isError) }
    exitProcess(0)
  }
}

suspend fun main(args: Array<String>) {
  WeatherCli().main(args)
}
