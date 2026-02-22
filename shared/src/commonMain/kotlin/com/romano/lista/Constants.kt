package com.romano.lista

const val SERVER_PORT = 8080
const val SERVER_URL = "http://localhost:$SERVER_PORT"
const val WEBSOCKET_URL = "ws://localhost:$SERVER_PORT"

expect object LocalStorage {
    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()
}
