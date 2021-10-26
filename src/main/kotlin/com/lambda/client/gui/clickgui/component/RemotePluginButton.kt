package com.lambda.client.gui.clickgui.component

import com.lambda.client.gui.clickgui.LambdaClickGui
import com.lambda.client.gui.rgui.component.BooleanSlider
import com.lambda.client.plugin.PluginManager
import com.lambda.client.plugin.api.Plugin
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.defaultScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class RemotePluginButton(
    private val buttonName: String,
    val description: String,
    val authors: String,
    val version: String,
    val downloadUrl: String,
    val fileName: String
) : BooleanSlider(buttonName, 0.0, description) {
    override fun onTick() {
        super.onTick()
    }

    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)
        downloadPlugin()
    }

    override fun onRelease(mousePos: Vec2f, buttonId: Int) {
        super.onRelease(mousePos, buttonId)
    }

    private fun downloadPlugin() {
        defaultScope.launch(Dispatchers.IO) {
            try {
                URL(downloadUrl).openStream().use { `in` ->
                    Files.copy(`in`, Paths.get("${PluginManager.pluginPath}/$fileName"), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            PluginManager.getLoaders().forEach { it.load() }
            LambdaClickGui.updatePlugins()
        }
    }
}