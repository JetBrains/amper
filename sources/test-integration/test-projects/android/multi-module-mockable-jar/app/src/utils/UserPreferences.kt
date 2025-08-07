package com.jetbrains.sample.app.utils

import android.content.Context
import androidx.core.content.edit

class UserPreferences(private val context: Context) {
    private val PREF_NAME = "user_preferences"
    private val KEY_USERNAME = "username"
    private val KEY_USER_ID = "user_id"

    fun saveUsername(username: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_USERNAME, username) }
    }

    fun getUsername(): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USERNAME, "") ?: ""
    }

    fun saveUserId(id: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_USER_ID, id) }
    }

    fun getUserId(): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_USER_ID, -1)
    }
}
