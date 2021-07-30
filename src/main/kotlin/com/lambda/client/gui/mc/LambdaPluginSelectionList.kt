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

    override fun getListWidth(): Int {
        return super.getListWidth() + 85
    }

    override fun getScrollBarX(): Int {
        return super.getScrollBarX() + 30
    }

    fun collectPlugins(onlyLocal: Boolean = false) {

        loadedPlugins.forEach { plugin ->
            val exists = plugins.firstOrNull { it.pluginData.name == plugin.name } // contains a value if exists, is null if it doesn't
            if (exists != null) {
                exists.pluginData.pluginState = PluginState.INSTALLED
            } else {
                plugins.add(LambdaPluginListEntry(owner, PluginData(plugin.name, PluginState.INSTALLED, version = plugin.version), plugin))
            }
        }

        getLoaders().forEach { loader ->
            val exists = plugins.firstOrNull { it.pluginData.name == loader.name } // contains a value if exists, is null if it doesn't
            if (exists != null) {
                exists.pluginData.pluginState = PluginState.AVAILABLE
            } else {
                plugins.add(LambdaPluginListEntry(owner, PluginData(loader.name, PluginState.AVAILABLE, version = loader.info.version), null, loader))
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
                            val downloadsJson = ConnectionUtils.runConnection(jsonElement.asJsonObject.get("releases_url").asString.replace("{/id}", ""), { connection ->
                                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                                connection.requestMethod = "GET"
                                connection.inputStream.readBytes().toString(Charsets.UTF_8)
                            })


                            if (JsonParser().parse(downloadsJson).asJsonArray.size() > 0) {
                                val mostRecentVersion = JsonParser().parse(downloadsJson).asJsonArray[0]
                                val name = jsonElement.asJsonObject.get("name").asString
                                if (plugins.none { it.pluginData.name == name } &&
                                    loadedPlugins.none { it.name == name }) {
                                    plugins.add(LambdaPluginListEntry(owner, PluginData(name, PluginState.REMOTE, jsonElement.asJsonObject.get("description").asString, mostRecentVersion.asJsonObject.get("tag_name").asString)))
                                } else {
                                    plugins.find { it.pluginData.name == name }?.let { plugin ->
                                        plugin.onlineVersion = mostRecentVersion.asJsonObject.get("tag_name").asString
                                    }
                                }
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
        REMOTE("Remote", "Download", 0x752AD1),
        UPDATE("Update Available", "Update",0x7F8DEB) // TODO find a better colour for this
    }

    data class PluginData(val name: String, var pluginState: PluginState, var repoDescription: String = "", var version: String)
}