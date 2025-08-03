/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A [DateTimeFormat] that produces a string in the format `yyyy-MM-dd_HH-mm-ss`, which is suitable for use in
 * filenames.
 */
@OptIn(FormatStringsInDatetimeFormats::class)
internal val DateTimeFormatForFilenames: DateTimeFormat<LocalDateTime> =
    LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd_HH-mm-ss") }

/**
 * Returns the current [LocalDateTime] in the system's default [TimeZone].
 */
internal fun LocalDateTime.Companion.nowInDefaultTimezone(): LocalDateTime =
    Clock.System.now().toLocalDateTimeInDefaultTimezone()

/**
 * Returns the [LocalDateTime] corresponding to this [Instant] in the system's default [TimeZone].
 */
internal fun Instant.toLocalDateTimeInDefaultTimezone(): LocalDateTime =
    toLocalDateTime(TimeZone.currentSystemDefault())
