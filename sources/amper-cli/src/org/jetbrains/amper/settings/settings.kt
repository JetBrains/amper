/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.settings

import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.tasks.identificationPhrase

/**
 * Gets a single common value for a particular setting among these fragments, and throws an exception if 2 fragments
 * have a different value for it, or if there are no fragments at all.
 *
 * The setting is accessed using the provided [selector]. [settingFqn] is only used for error reporting.
 */
internal fun <T : Any> List<Fragment>.unanimousSetting(settingFqn: String, selector: (Settings) -> T): T =
    unanimousOptionalSetting(settingFqn, selector)
        ?: error("No fragments provided, cannot merge setting '$settingFqn'")

/**
 * Gets a single common value for a particular setting among these fragments, and throws an exception if 2 fragments
 * have a different value for it. If a fragment doesn't specify a value for the setting (null), it doesn't count as
 * having a different value and doesn't throw an exception.
 *
 * The setting is accessed using the provided [selector]. [settingFqn] is only used for error reporting.
 */
internal fun <T> List<Fragment>.unanimousOptionalSetting(settingFqn: String, selector: (Settings) -> T): T? {
    val distinctValues = mapNotNull { selector(it.settings) }.distinct()
    if (distinctValues.size > 1) {
        error("${identificationPhrase()} are compiled " +
                "together but provide several different values for 'settings.$settingFqn': $distinctValues")
    }
    return distinctValues.singleOrNull()
}
