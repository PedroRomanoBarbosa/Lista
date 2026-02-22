package com.romano.lista

import kotlinx.serialization.Serializable

@Serializable
enum class ItemState {
    MISSING, BUYING, DONE
}

@Serializable
data class User(
    val id: String,
    val name: String
)

@Serializable
data class ShoppingItem(
    val id: String,
    val name: String,
    val quantity: Int,
    val state: ItemState,
    val createdBy: String, // User ID
    val buyingUser: String? = null // User ID if state is BUYING
)

@Serializable
data class LoginRequest(
    val token: String
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val user: User? = null,
    val message: String? = null
)

@Serializable
data class ItemsResponse(
    val items: List<ShoppingItem>,
    val users: List<User>
)

@Serializable
data class AddItemRequest(
    val name: String,
    val quantity: Int
)

@Serializable
data class UpdateItemStateRequest(
    val itemId: String,
    val state: ItemState,
    val buyingUser: String? = null
)

@Serializable
data class DeleteItemRequest(
    val itemId: String
)

@Serializable
sealed class WebSocketMessage {
    @Serializable
    data class ItemsUpdated(val items: List<ShoppingItem>, val users: List<User>) : WebSocketMessage()

    @Serializable
    data class UserListUpdated(val users: List<User>) : WebSocketMessage()

    @Serializable
    data class ItemAdded(val item: ShoppingItem) : WebSocketMessage()

    @Serializable
    data class ItemUpdated(val item: ShoppingItem) : WebSocketMessage()

    @Serializable
    data class ItemDeleted(val itemId: String) : WebSocketMessage()

    @Serializable
    data class Error(val message: String) : WebSocketMessage()
}

