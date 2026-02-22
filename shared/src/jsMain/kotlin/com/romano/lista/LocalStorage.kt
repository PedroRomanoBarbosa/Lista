package com.romano.lista

actual object LocalStorage {
    actual fun saveToken(token: String) {
        try {
            js("localStorage.setItem('auth_token', token)")
        } catch (e: Exception) {
            // localStorage might not be available
        }
    }

    actual fun getToken(): String? {
        return try {
            js("localStorage.getItem('auth_token')").unsafeCast<String?>()
        } catch (e: Exception) {
            null
        }
    }

    actual fun clearToken() {
        try {
            js("localStorage.removeItem('auth_token')")
        } catch (e: Exception) {
            // localStorage might not be available
        }
    }
}

