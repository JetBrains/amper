/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package com.jetbrains.sample.app

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.parcelableCreator

@Parcelize
class User(val firstName: String, val lastName: String, val age: Int) : Parcelable {

    companion object {
        fun fromParcel(parcel: Parcel): User = parcelableCreator<User>().createFromParcel(parcel)
    }
}
