package io.github.xxfast.slides

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform