package com.romano.lista

actual object LocalStorage {
    private val storage = mutableMapOf<String, String>()

    actual fun saveToken(token: String) {
        storage["auth_token"] = token
    }

    actual fun getToken(): String? {
        return storage["auth_token"]
    }

    actual fun clearToken() {
        storage.remove("auth_token")
    }
}

