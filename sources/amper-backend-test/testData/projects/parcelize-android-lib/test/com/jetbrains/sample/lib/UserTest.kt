/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package com.jetbrains.sample.lib

// FIXME this is disabled because Android's android.os.Parcel class is apparently not on the test classpath
// https://youtrack.jetbrains.com/issue/AMPER-3337/Android-runtime-library-is-not-on-the-test-classpath

//import android.os.Parcel
//import android.os.Parcelable
import kotlin.test.Test
import kotlin.test.assertEquals

class UserTest {

    @Test
    fun testParcelizeUser() {
//        val user = User("John", "Doe", 25)
//
//        val parcel = Parcel.obtain()
//        user.writeToParcel(parcel, 0)
//
//        // Reset the parcel for reading
//        parcel.setDataPosition(0)
//
//        val createdFromParcel = User.fromParcel(parcel)
//        assertEquals(user, createdFromParcel)
//
//        parcel.recycle()
    }
}
