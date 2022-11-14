package com.lambda.client.plugin.api

import com.lambda.client.util.interfaces.Nameable

interface IPluginClass : Nameable {
    val pluginMain: Plugin
}