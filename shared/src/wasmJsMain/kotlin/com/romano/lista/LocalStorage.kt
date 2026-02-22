package com.romano.lista

// External declarations for localStorage access in WASM
external object localStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
}

actual object LocalStorage {
    actual fun saveToken(token: String) {
        try {
            localStorage.setItem("auth_token", token)
        } catch (e: Exception) {
            // localStorage might not be available
        }
    }

    actual fun getToken(): String? {
        return try {
            localStorage.getItem("auth_token")
        } catch (e: Exception) {
            null
        }
    }

    actual fun clearToken() {
        try {
            localStorage.removeItem("auth_token")
        } catch (e: Exception) {
            // localStorage might not be available
        }
    }
}

