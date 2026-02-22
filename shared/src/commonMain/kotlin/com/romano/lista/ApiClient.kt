package com.romano.lista

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ApiClient(private val serverUrl: String = SERVER_URL) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            })
        }
    }

    init {
        if (serverUrl.startsWith("ws://") || serverUrl.startsWith("wss://")) {
            throw IllegalArgumentException("ApiClient must use HTTP URL, not WebSocket URL")
        }
    }

    suspend fun login(token: String): Result<LoginResponse> = try {
        val response = client.post("$serverUrl/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(token))
        }
        Result.success(response.body())
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun addItem(token: String, name: String, quantity: Int): Result<ShoppingItem> = try {
        val response = client.post("$serverUrl/items") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(AddItemRequest(name, quantity))
        }
        Result.success(response.body())
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateItemState(token: String, itemId: String, state: ItemState, buyingUser: String? = null): Result<ShoppingItem> = try {
        val response = client.put("$serverUrl/items/$itemId") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(UpdateItemStateRequest(itemId, state, buyingUser))
        }
        Result.success(response.body())
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteItem(token: String, itemId: String): Result<Boolean> = try {
        client.delete("$serverUrl/items/$itemId") {
            header("Authorization", "Bearer $token")
        }
        Result.success(true)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

