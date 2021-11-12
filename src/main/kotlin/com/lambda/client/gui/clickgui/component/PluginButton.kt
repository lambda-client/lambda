package com.lambda.client.gui.clickgui.component

import com.lambda.client.gui.rgui.component.BooleanSlider
import com.lambda.client.plugin.PluginLoader
import com.lambda.client.plugin.PluginManager
import com.lambda.client.plugin.api.Plugin
import com.lambda.client.util.ConfigUtils
import com.lambda.client.util.math.Vec2f
import java.io.File

class PluginButton(var plugin: Plugin, var file: File) : BooleanSlider(plugin.name, 0.0, plugin.description) {
    init {
        if (plugin.isLoaded) value = 1.0
    }

    override fun onTick() {
        super.onTick()
        value = if (plugin.isLoaded) 1.0 else 0.0
    }

    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)
        if (buttonId == 0) {
            if (plugin.isLoaded) {
                ConfigUtils.saveAll()
                PluginManager.unload(plugin)
                plugin.isLoaded = false
            } else {
                PluginManager.load(PluginLoader(file))
                PluginManager.loadedPlugins[plugin.name]?.let {
                    plugin = it
                }
                ConfigUtils.loadAll()
                plugin.isLoaded = true
            }
        }
    }
}