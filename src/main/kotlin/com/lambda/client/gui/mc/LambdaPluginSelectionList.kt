package com.lambda.client.gui.mc

import com.google.gson.JsonParser
import com.lambda.client.LambdaMod
import com.lambda.client.plugin.PluginManager.getLoaders
import com.lambda.client.util.threads.defaultScope
import com.lambda.commons.utils.ConnectionUtils
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiListExtended

class LambdaPluginSelectionList(val owner: LambdaGuiPluginManager, mcIn: Minecraft, widthIn: Int, heightIn: Int, topIn: Int, bottomIn: Int, slotHeightIn: Int) : GuiListExtended(
    mcIn,
    widthIn,
    heightIn,
    topIn,
    bottomIn,
    slotHeightIn
) {
    private var remotePlugins = mutableListOf<LambdaPluginListEntry>()
    var selectedSlotIndex = -1

    override fun getSize(): Int {
        return remotePlugins.size
    }

    override fun getListEntry(index: Int): IGuiListEntry {
        return remotePlugins[index]
    }

    override fun isSelected(slotIndex: Int): Boolean {
        return slotIndex == selectedSlotIndex
    }

    fun collectPlugins() {
        val localPlugins = getLoaders().sortedBy { it.name }

        defaultScope.launch {
            try {
                val rawJson = ConnectionUtils.runConnection(LambdaMod.PLUGIN_LINK, { connection ->
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connection.requestMethod = "GET"
                    connection.inputStream.readBytes().toString(Charsets.UTF_8)
                }) {
                    LambdaMod.LOG.error("Failed to load organisation for plugins from GitHub", it)
                }

                rawJson?.let { json ->
                    val jsonTree = JsonParser().parse(json).asJsonArray

                    jsonTree.forEach { jsonElement ->
                        val name = jsonElement.asJsonObject.get("name").asString
                        if (!remotePlugins.any { it.pluginData.name == name } &&
                            !localPlugins.any { it.name == name }) {
                            remotePlugins.add(LambdaPluginListEntry(owner, PluginData(name, PluginState.REMOTE)))
                        }
                    }

                    LambdaMod.LOG.info("Found remote plugins: ${jsonTree.size()}")
                }
            } catch (e: Exception) {
                LambdaMod.LOG.error("Failed to parse plugin json", e)
            }
        }
    }

    enum class PluginState {
        REMOTE, LOADED, INSTALLED
    }

    data class PluginData(val name: String, var pluginState: PluginState)
}