package com.lambda.client.gui.mc

import com.google.gson.JsonParser
import com.lambda.client.LambdaMod
import com.lambda.client.plugin.PluginManager.getLoaders
import com.lambda.client.plugin.PluginManager.loadedPlugins
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
    private var plugins = mutableListOf<LambdaPluginListEntry>()
    var selectedSlotIndex = -1

    override fun getSize(): Int {
        return plugins.size
    }

    override fun getListEntry(index: Int): IGuiListEntry {
        return plugins[index]
    }

    override fun isSelected(slotIndex: Int): Boolean {
        return slotIndex == selectedSlotIndex
    }

    fun collectPlugins(onlyLocal: Boolean = false) {

        loadedPlugins.forEach { plugin ->
            plugins.firstOrNull { it.pluginData.name == plugin.name }?.let { entry ->
                plugins.remove(entry)
            }
            plugins.add(LambdaPluginListEntry(owner, PluginData(plugin.name, PluginState.INSTALLED), plugin))
        }

        getLoaders().forEach { loader ->
            if (loadedPlugins.none { it.name == loader.name }) {
                plugins.firstOrNull { it.pluginData.name == loader.name }?.let { entry ->
                    plugins.remove(entry)
                }
                plugins.add(LambdaPluginListEntry(owner, PluginData(loader.name, PluginState.AVAILABLE), null, loader))
            }
        }

        if (!onlyLocal) {
            defaultScope.launch {
                try {
                    val rawJson = ConnectionUtils.runConnection(LambdaMod.GITHUB_API + "orgs/" + LambdaMod.ORGANIZATION + "/repos", { connection ->
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
                            if (plugins.none { it.pluginData.name == name } &&
                                loadedPlugins.none { it.name == name }) {
                                plugins.add(LambdaPluginListEntry(owner, PluginData(name, PluginState.REMOTE, jsonElement.asJsonObject.get("description").asString)))
                            }
                        }

                        LambdaMod.LOG.info("Found remote plugins: ${jsonTree.size()}")
                    }
                } catch (e: Exception) {
                    LambdaMod.LOG.error("Failed to parse plugin json", e)
                }
            }
        }

        plugins.sortWith(
            compareBy<LambdaPluginListEntry> {
                it.pluginData.pluginState.ordinal
            }.thenBy {
                it.pluginData.name
            }
        )
    }

    enum class PluginState(val displayName: String, val buttonName: String, val color: Int) {
        LOADING("Loading", "Loading", 0x808080),
        INSTALLED("Installed", "Uninstall", 0x2AD13B),
        AVAILABLE("Available", "Install", 0x4287F5),
        REMOTE("Remote", "Download", 0x752AD1)
    }

    data class PluginData(val name: String, var pluginState: PluginState, var repoDescription: String = "")
}