package com.jetbrains.sample.lib

import android.os.Parcel
import kotlinx.parcelize.*

fun userFromParcel(parcel: Parcel): User = parcelableCreator<User>().createFromParcel(parcel)
