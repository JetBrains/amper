/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.github.singleton11.myapplication

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap


class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("my-app", getString(R.string.app_name))
        val userId: BiMap<String, Int> = HashBiMap.create()
        userId.put("guest", 2)
        Log.d("user-id", userId.inverse()[2] ?: "none")
        setContentView(R.layout.main)
        super.onCreate(savedInstanceState)
    }
}
