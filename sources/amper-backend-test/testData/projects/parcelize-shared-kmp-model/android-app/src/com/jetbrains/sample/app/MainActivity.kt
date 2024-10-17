/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.jetbrains.sample.app

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.os.Parcel
import com.jetbrains.sample.lib.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.main)
        super.onCreate(savedInstanceState)

        val parcel = Parcel.obtain()  // Obtain a Parcel instance from the pool
        parcel.writeString("John")
        parcel.writeString("Doe")
        parcel.writeInt(43)

        parcel.setDataPosition(0)

        userFromParcel(parcel)
    }
}
