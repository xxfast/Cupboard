package io.github.xxfast.cupboard

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform