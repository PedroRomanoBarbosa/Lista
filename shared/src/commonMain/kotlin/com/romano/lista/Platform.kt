package com.romano.lista

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform