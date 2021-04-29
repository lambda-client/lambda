package com.lambda.client

import com.lambda.client.command.CommandManager
import com.lambda.client.gui.GuiManager
import com.lambda.client.manager.ManagerLoader
import com.lambda.client.module.ModuleManager
import com.lambda.client.plugin.PluginManager
import com.lambda.client.util.threads.mainScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

internal object LoaderWrapper {
    private val loaderList = ArrayList<com.lambda.client.AsyncLoader<*>>()

    init {
        com.lambda.client.LoaderWrapper.loaderList.add(ModuleManager)
        com.lambda.client.LoaderWrapper.loaderList.add(CommandManager)
        com.lambda.client.LoaderWrapper.loaderList.add(ManagerLoader)
        com.lambda.client.LoaderWrapper.loaderList.add(GuiManager)
        com.lambda.client.LoaderWrapper.loaderList.add(PluginManager)
    }

    @JvmStatic
    fun preLoadAll() {
        com.lambda.client.LoaderWrapper.loaderList.forEach { it.preLoad() }
    }

    @JvmStatic
    fun loadAll() {
        runBlocking {
            com.lambda.client.LoaderWrapper.loaderList.forEach { it.load() }
        }
    }
}

internal interface AsyncLoader<T> {
    var deferred: Deferred<T>?

    fun preLoad() {
        deferred = preLoadAsync()
    }

    private fun preLoadAsync(): Deferred<T> {
        return mainScope.async { preLoad0() }
    }

    suspend fun load() {
        load0((deferred ?: preLoadAsync()).await())
    }

    fun preLoad0(): T
    fun load0(input: T)
}