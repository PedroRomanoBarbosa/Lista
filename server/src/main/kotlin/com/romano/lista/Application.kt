package com.romano.lista

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import java.util.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

// Application state holder
class AppState {
    val shoppingItems: MutableList<ShoppingItem> = mutableListOf()
    val users: MutableList<User> = mutableListOf()
    val connectedSessions: MutableList<WebSocketSession> = mutableListOf()
    val sessionUserMap: MutableMap<WebSocketSession, String> = mutableMapOf()
    val mutex = Mutex()
}

// In-memory storage
private val appState = AppState()
private val userTokens = mapOf(
    "user1" to User("user1", "Alice"),
    "user2" to User("user2", "Bob"),
    "user3" to User("user3", "Charlie")
)
private val jsonSerializer = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

fun Application.module() {
    install(WebSockets)
    install(ContentNegotiation) {
        json(jsonSerializer)
    }

    appState.users.addAll(userTokens.values)

    routing {
        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = userTokens[request.token]
            if (user != null) {
                call.respond(LoginResponse(success = true, user = user))
            } else {
                call.respond(HttpStatusCode.Unauthorized, LoginResponse(success = false, message = "Invalid token"))
            }
        }

        get("/items") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            if (token == null || !userTokens.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized, LoginResponse(success = false, message = "Unauthorized"))
                return@get
            }
            call.respond(ItemsResponse(appState.shoppingItems.toList(), appState.users.toList()))
        }

        post("/items") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            if (token == null || !userTokens.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            val request = call.receive<AddItemRequest>()
            val user = userTokens[token]!!
            val newItem = ShoppingItem(
                id = UUID.randomUUID().toString(),
                name = request.name,
                quantity = request.quantity,
                state = ItemState.MISSING,
                createdBy = user.id
            )
            appState.mutex.lock()
            try {
                appState.shoppingItems.add(newItem)
                broadcastItemsUpdate()
            } finally {
                appState.mutex.unlock()
            }
            call.respond(HttpStatusCode.Created, newItem)
        }

        put("/items/{itemId}") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            if (token == null || !userTokens.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@put
            }
            val itemId = call.parameters["itemId"] ?: return@put
            val request = call.receive<UpdateItemStateRequest>()
            val user = userTokens[token]!!

            appState.mutex.lock()
            try {
                val index = appState.shoppingItems.indexOfFirst { it.id == itemId }
                if (index == -1) {
                    call.respond(HttpStatusCode.NotFound)
                    return@put
                }
                val item = appState.shoppingItems[index]
                val updatedItem = item.copy(
                    state = request.state,
                    buyingUser = if (request.state == ItemState.BUYING) user.id else null
                )
                appState.shoppingItems[index] = updatedItem
                broadcastItemsUpdate()
                call.respond(updatedItem)
            } finally {
                appState.mutex.unlock()
            }
        }

        delete("/items/{itemId}") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            if (token == null || !userTokens.containsKey(token)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@delete
            }
            val itemId = call.parameters["itemId"] ?: return@delete
            val user = userTokens[token]!!

            appState.mutex.lock()
            try {
                val item = appState.shoppingItems.find { it.id == itemId }
                if (item == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@delete
                }
                if (item.createdBy != user.id) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only creator can delete"))
                    return@delete
                }
                appState.shoppingItems.removeIf { it.id == itemId }
                broadcastItemsUpdate()
                call.respond(HttpStatusCode.NoContent)
            } finally {
                appState.mutex.unlock()
            }
        }

        webSocket("/ws") {
            try {
                // Get auth token from initial message
                var authenticated = false
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        if (text.contains("auth")) {
                            // Parse token and authenticate
                            val tokenRegex = """"token":"([^"]+)"""".toRegex()
                            val token = tokenRegex.find(text)?.groupValues?.get(1)

                            if (token != null && userTokens.containsKey(token)) {
                                appState.sessionUserMap[this] = token
                                appState.connectedSessions.add(this)
                                authenticated = true

                                // Send current state
                                val response = WebSocketMessage.ItemsUpdated(appState.shoppingItems, appState.users)
                                send(Frame.Text(jsonSerializer.encodeToString(response)))
                                break // Break to continue listening for updates in main loop
                            } else {
                                send(Frame.Text(jsonSerializer.encodeToString(WebSocketMessage.Error("Unauthorized"))))
                                return@webSocket
                            }
                        }
                    }
                }

                // If authenticated, continue listening for incoming messages
                if (authenticated) {
                    for (inFrame in incoming) {
                        // Read and discard frames - not currently processing client messages
                        if (inFrame is Frame.Text) inFrame.readText()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                appState.sessionUserMap.remove(this)
                appState.connectedSessions.remove(this)
            }
        }
    }
}

private suspend fun broadcastItemsUpdate() {
    val response = WebSocketMessage.ItemsUpdated(appState.shoppingItems, appState.users)
    val message = jsonSerializer.encodeToString(response)
    appState.connectedSessions.forEach { session ->
        try {
            session.send(Frame.Text(message))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

