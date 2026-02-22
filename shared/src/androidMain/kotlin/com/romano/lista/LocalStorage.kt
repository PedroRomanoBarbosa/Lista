package com.romano.lista

import android.content.Context
import android.content.SharedPreferences

actual object LocalStorage {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("lista_prefs", Context.MODE_PRIVATE)
    }

    actual fun saveToken(token: String) {
        prefs.edit().putString("auth_token", token).apply()
    }

    actual fun getToken(): String? {
        return prefs.getString("auth_token", null)
    }

    actual fun clearToken() {
        prefs.edit().remove("auth_token").apply()
    }
}

