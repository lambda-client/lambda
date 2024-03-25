package com.lambda.core

interface Loadable {
    fun load() = this::class.simpleName?.let { "Loaded $it" } ?: "Loaded"
}