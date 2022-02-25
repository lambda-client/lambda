package com.lambda.client.plugin.api

import com.lambda.client.commons.interfaces.Nameable

interface IPluginClass : Nameable {
    val pluginMain: Plugin
}