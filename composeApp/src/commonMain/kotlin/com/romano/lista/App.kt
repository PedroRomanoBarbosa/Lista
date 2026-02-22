package com.romano.lista

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun App() {
    MaterialTheme {
        var currentUser by remember { mutableStateOf<User?>(null) }
        var token by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            val savedToken = LocalStorage.getToken()
            if (savedToken != null) {
                token = savedToken
                // Try to validate token by login
                val apiClient = ApiClient()
                val result = apiClient.login(savedToken)
                result.onSuccess { response ->
                    if (response.success && response.user != null) {
                        currentUser = response.user
                    } else {
                        LocalStorage.clearToken()
                    }
                }
            }
        }

        if (currentUser == null || token == null) {
            LoginScreen(
                onLoginSuccess = { user, authToken ->
                    currentUser = user
                    token = authToken
                    LocalStorage.saveToken(authToken)
                }
            )
        } else {
            MainApp(currentUser!!, token!!)
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (User, String) -> Unit) {
    var selectedToken by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val hardcodedTokens = listOf(
        "user1" to "Alice",
        "user2" to "Bob",
        "user3" to "Charlie"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .verticalScroll(rememberScrollState())
                .width(400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Shopping List",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Select a user to login",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            hardcodedTokens.forEach { (token, name) ->
                Button(
                    onClick = {
                        selectedToken = token
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val apiClient = ApiClient()
                                val result = apiClient.login(token)
                                result.onSuccess { response ->
                                    if (response.success) {
                                        response.user?.let { user ->
                                            onLoginSuccess(user, token)
                                        } ?: run {
                                            errorMessage = "User data missing"
                                            isLoading = false
                                        }
                                    } else {
                                        errorMessage = response.message ?: "Login failed"
                                        isLoading = false
                                    }
                                }
                                result.onFailure { e ->
                                    errorMessage = e.message ?: "Network error"
                                    isLoading = false
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Error"
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text(name)
                }
            }

            if (errorMessage != null) {
                SelectionContainer {
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun MainApp(currentUser: User, token: String) {
    var items by remember { mutableStateOf<List<ShoppingItem>>(emptyList()) }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var isConnected by remember { mutableStateOf(false) }
    var selectedUserTab by remember { mutableStateOf(0) }
    var showHistory by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var newItemQuantity by remember { mutableStateOf("1") }

    val scope = rememberCoroutineScope()
    val wsClient = remember { WebSocketClient() }
    val apiClient = remember { ApiClient() }

    LaunchedEffect(token) {
        scope.launch {
            wsClient.connect(token)
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            wsClient.connectionState.collect { connected ->
                isConnected = connected
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Disconnect banner
            if (!isConnected) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    color = MaterialTheme.colorScheme.error
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⚠ Disconnected from server",
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Top user bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    reverseLayout = false
                ) {
                    // TODO: render user tabs
                }
            }

            // Main content area
            if (!showHistory) {
                MainListScreen(
                    currentUser = currentUser,
                    token = token,
                    items = items,
                    users = users,
                    onShowHistory = { showHistory = true },
                    apiClient = apiClient
                )
            } else {
                HistoryScreen(
                    items = items.filter { it.state == ItemState.DONE },
                    onBack = { showHistory = false }
                )
            }
        }
    }
}

@Composable
fun MainListScreen(
    currentUser: User,
    token: String,
    items: List<ShoppingItem>,
    users: List<User>,
    onShowHistory: () -> Unit,
    apiClient: ApiClient
) {
    var newItemName by remember { mutableStateOf("") }
    var newItemQuantity by remember { mutableStateOf("1") }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    label = { Text("Item name") },
                    modifier = Modifier.weight(1f)
                )
                TextField(
                    value = newItemQuantity,
                    onValueChange = { newItemQuantity = it },
                    label = { Text("Qty") },
                    modifier = Modifier.width(60.dp)
                )
                Button(onClick = {
                    if (newItemName.isNotEmpty() && newItemQuantity.toIntOrNull() != null) {
                        scope.launch {
                            apiClient.addItem(token, newItemName, newItemQuantity.toInt())
                            newItemName = ""
                            newItemQuantity = "1"
                        }
                    }
                }) {
                    Text("Add")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Missing items (${items.filter { it.state != ItemState.DONE }.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Button(onClick = onShowHistory) {
                    Text("History")
                }
            }
        }

        items(items.filter { it.state != ItemState.DONE }) { item ->
            ItemRow(
                item = item,
                currentUser = currentUser,
                users = users,
                onStateChange = { newState, buyingUser ->
                    scope.launch {
                        apiClient.updateItemState(token, item.id, newState, buyingUser)
                    }
                },
                onDelete = {
                    scope.launch {
                        apiClient.deleteItem(token, item.id)
                    }
                }
            )
        }
    }
}

@Composable
fun ItemRow(
    item: ShoppingItem,
    currentUser: User,
    users: List<User>,
    onStateChange: (ItemState, String?) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Qty: ${item.quantity} | Created by: ${users.find { it.id == item.createdBy }?.name ?: "Unknown"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.state == ItemState.BUYING) {
                        val buyerName = users.find { it.id == item.buyingUser }?.name ?: "Unknown"
                        Text(
                            "🛒 Buying: $buyerName",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (item.state) {
                    ItemState.MISSING -> {
                        Button(
                            onClick = { onStateChange(ItemState.BUYING, currentUser.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("I'll buy it")
                        }
                    }
                    ItemState.BUYING -> {
                        if (item.buyingUser == currentUser.id) {
                            Button(
                                onClick = { onStateChange(ItemState.DONE, currentUser.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Done")
                            }
                        }
                    }
                    ItemState.DONE -> {}
                }

                if (item.createdBy == currentUser.id) {
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(0.8f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(items: List<ShoppingItem>, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Completed items (${items.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Button(onClick = onBack) {
                    Text("Back")
                }
            }
        }

        items(items) { item ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(item.name, fontWeight = FontWeight.Bold)
                    Text("Qty: ${item.quantity}", fontSize = 12.sp)
                    Text("✓ Completed", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
        }
    }
}

