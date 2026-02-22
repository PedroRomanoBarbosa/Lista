package com.romano.lista

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WebSocketClient(private val serverUrl: String = WEBSOCKET_URL) {
    private var session: WebSocketSession? = null
    private val client = HttpClient {
        install(WebSockets)
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            })
        }
    }

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<WebSocketMessage>(replay = 0)
    val messages: SharedFlow<WebSocketMessage> = _messages.asSharedFlow()

    private var connectionJob: Job? = null

    suspend fun connect(token: String) {
        // Cancel any existing connection job
        connectionJob?.cancel()

        connectionJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                client.webSocket(serverUrl) {
                    _connectionState.value = true
                    session = this

                    // Send authentication message
                    val authMessage = """{"type":"auth","token":"$token"}"""
                    send(Frame.Text(authMessage))

                    // Listen for messages
                    try {
                        for (message in incoming) {
                            if (message is Frame.Text) {
                                val text = message.readText()
                                try {
                                    val wsMessage = parseMessage(text)
                                    _messages.emit(wsMessage)
                                } catch (e: Exception) {
                                    println("Failed to parse message: $text, error: ${e.message}")
                                }
                            }
                        }
                    } finally {
                        _connectionState.value = false
                        session = null
                    }
                }
            } catch (e: Exception) {
                println("WebSocket connection error: ${e.message}")
                e.printStackTrace()
                _connectionState.value = false
                session = null
            }
        }
    }

    suspend fun disconnect() {
        _connectionState.value = false
        connectionJob?.cancel()
        session?.close()
        session = null
        connectionJob = null
    }

    suspend fun sendMessage(message: String) {
        try {
            session?.send(Frame.Text(message))
        } catch (e: Exception) {
            println("Failed to send message: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun parseMessage(text: String): WebSocketMessage {
        return try {
            val json = Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }

            // First, try to deserialize directly to WebSocketMessage
            json.decodeFromString<WebSocketMessage>(text)
        } catch (e: Exception) {
            // Fallback: try to extract type and parse accordingly
            try {
                val jsonElement = Json.parseToJsonElement(text)
                val jsonObj = jsonElement.jsonObject

                when (jsonObj["type"]?.jsonPrimitive?.content) {
                    "items_updated" -> {
                        WebSocketMessage.ItemsUpdated(emptyList(), emptyList())
                    }
                    "error" -> {
                        val message = jsonObj["message"]?.jsonPrimitive?.content ?: "Unknown error"
                        WebSocketMessage.Error(message)
                    }
                    else -> {
                        WebSocketMessage.Error("Unknown message type")
                    }
                }
            } catch (e2: Exception) {
                WebSocketMessage.Error("Failed to parse message: ${e.message}")
            }
        }
    }

    fun isConnected(): Boolean = _connectionState.value
}

