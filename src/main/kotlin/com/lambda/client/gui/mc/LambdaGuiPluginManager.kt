package com.lambda.client.gui.mc

import com.google.common.base.Splitter
import com.google.common.collect.Lists
import com.google.gson.JsonParser
import com.lambda.client.LambdaMod
import com.lambda.client.plugin.PluginManager
import com.lambda.client.plugin.PluginManager.load
import com.lambda.client.plugin.PluginManager.unload
import com.lambda.client.util.threads.defaultScope
import com.lambda.commons.utils.ConnectionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

internal class LambdaGuiPluginManager(private val previousScreen: GuiScreen) : GuiScreen() {
    private lateinit var pluginListSelector: LambdaPluginSelectionList
    private var renderTime = 1
    var hoveringText = ""

    override fun initGui() {
        pluginListSelector = LambdaPluginSelectionList(this, mc, width, height, 32, height - 64, 36)
        pluginListSelector.collectPlugins()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (renderTime % 50 == 0) pluginListSelector.collectPlugins(true)

        buttonList.clear()

        drawDefaultBackground()
        pluginListSelector.drawScreen(mouseX, mouseY, partialTicks)

        drawCenteredString(fontRenderer, "Plugin Manager", width / 2, 20, 16777215)
        buttonList.add(GuiButton(0, width / 2 - 50, height - 50, 100, 20, "Back"))
        buttonList.add(GuiButton(1, width / 2 - 180, height - 50, 120, 20, "Open Plugins Folder"))
        if (pluginListSelector.selectedSlotIndex > -1) {
            val pluginEntry = pluginListSelector.getListEntry(pluginListSelector.selectedSlotIndex) as LambdaPluginListEntry
            val button = GuiButton(2, width / 2 + 60, height - 50, 120, 20, pluginEntry.pluginData.pluginState.buttonName)
            if (pluginEntry.pluginData.pluginState == LambdaPluginSelectionList.PluginState.LOADING) button.enabled = false
            buttonList.add(button)
        } else {
            val button = GuiButton(2, width / 2 + 60, height - 50, 120, 20, "Select Plugin")
            button.enabled = false
            buttonList.add(button)
        }

        renderTime++
        super.drawScreen(mouseX, mouseY, partialTicks)

        if (hoveringText != "") {
            drawHoveringText(Lists.newArrayList(Splitter.on("\n").split(hoveringText)), mouseX, mouseY)
        }
    }

    override fun updateScreen() {
        //
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> mc.displayGuiScreen(previousScreen)
            1 -> Desktop.getDesktop().open(File(PluginManager.pluginPath))
            2 -> {
                val pluginEntry = (pluginListSelector.getListEntry(pluginListSelector.selectedSlotIndex) as LambdaPluginListEntry)
                when (pluginEntry.pluginData.pluginState) {
                    LambdaPluginSelectionList.PluginState.REMOTE -> {
                        downloadPlugin(pluginEntry)
                    }
                    LambdaPluginSelectionList.PluginState.AVAILABLE -> {
                        pluginEntry.loader?.let {
                            load(it)
                        }
                    }
                    LambdaPluginSelectionList.PluginState.INSTALLED -> {
                        pluginEntry.plugin?.let {
                            unload(it)
                        }
                    }
                    LambdaPluginSelectionList.PluginState.LOADING -> {
                        //
                    }
                }
                pluginEntry.pluginData.pluginState = LambdaPluginSelectionList.PluginState.LOADING
            }
        }
    }

    @Throws(IOException::class)
    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        super.mouseClicked(mouseX, mouseY, mouseButton)
        pluginListSelector.mouseClicked(mouseX, mouseY, mouseButton)
    }

    @Throws(IOException::class)
    override fun handleMouseInput() {
        super.handleMouseInput()
        pluginListSelector.handleMouseInput()
    }

    fun selectPlugin(index: Int) {
        pluginListSelector.selectedSlotIndex = index
    }

    private fun downloadPlugin(pluginEntry: LambdaPluginListEntry) {
        var pluginDownloadUrl = ""
        var fileName = ""

        defaultScope.launch(Dispatchers.IO) {
            try {
                val rawJson = ConnectionUtils.runConnection("${LambdaMod.GITHUB_API}repos/${LambdaMod.ORGANIZATION}/${pluginEntry.pluginData.name}/releases", { connection ->
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connection.requestMethod = "GET"
                    connection.inputStream.readBytes().toString(Charsets.UTF_8)
                }) {
                    LambdaMod.LOG.error("Failed to load repo of plugin from GitHub", it)
                }

                rawJson?.let { json ->
                    val jsonTree = JsonParser().parse(json).asJsonArray

                    jsonTree[0]?.let { jsonElement ->
                        val assets = jsonElement.asJsonObject.get("assets").asJsonArray
                        assets[0]?.let {
                            pluginDownloadUrl = it.asJsonObject.get("browser_download_url").asString
                            fileName = it.asJsonObject.get("name").asString
                        }
                    }

                    LambdaMod.LOG.info("Found remote plugins: ${jsonTree.size()}")
                }

            } catch (e: Exception) {
                LambdaMod.LOG.error("Failed to parse plugin json", e)
            }

            try {
                URL(pluginDownloadUrl).openStream().use { `in` ->
                    Files.copy(`in`, Paths.get("${PluginManager.pluginPath}/$fileName"), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

}