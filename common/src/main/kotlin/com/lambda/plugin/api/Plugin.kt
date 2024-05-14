package com.lambda.plugin.api

import com.lambda.util.Nameable

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
    open fun preLoad() {} // This is invoked before the game launches
    abstract fun load()
}
