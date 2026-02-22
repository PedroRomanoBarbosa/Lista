package com.romano.lista

import java.util.prefs.Preferences

actual object LocalStorage {
    private val prefs: Preferences = Preferences.userRoot().node("lista")

    actual fun saveToken(token: String) {
        prefs.put("auth_token", token)
    }

    actual fun getToken(): String? {
        return prefs.get("auth_token", null)
    }

    actual fun clearToken() {
        prefs.remove("auth_token")
    }
}

