package com.lambda.client.plugin

import com.lambda.client.plugin.api.Plugin
import com.lambda.commons.interfaces.Nameable

internal interface IPluginLoader : Nameable {
    override val name: String get() = info.name

    val info: PluginInfo

    fun verify(): Boolean

    fun load(): Plugin

    fun close()
}