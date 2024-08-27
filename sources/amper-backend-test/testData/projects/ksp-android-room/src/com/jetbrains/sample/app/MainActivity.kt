/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.jetbrains.sample.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    suspend fun doSomething() {
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "database-name").build()
        val userDao = db.userDao()
        val users = userDao.getAll()

        // reference to generated files to test that compilation has them on the compile classpath
        AppDatabase_Impl()
        UserDao_Impl(db)
    }
}
