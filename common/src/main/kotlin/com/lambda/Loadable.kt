package com.lambda

interface Loadable {
    fun load() = this::class.simpleName?.let { "Loaded $it" } ?: "Loaded"
}