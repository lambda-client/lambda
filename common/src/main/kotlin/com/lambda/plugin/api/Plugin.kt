package com.lambda.plugin.api

import com.lambda.util.Nameable

// TODO: Make this api better
abstract class Plugin(
    override val name: String,
    val description: String,
    val version: String,
    val author: List<String>,
    val dependencies: List<String>? = null,
    val softDependencies: List<String>? = null,
    val loadBefore: List<String>? = null,
    val loadAfter: List<String>? = null,
) : Nameable {
    abstract fun load()
}
