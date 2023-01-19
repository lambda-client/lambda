package com.lambda.client.gui.clickgui

import com.google.gson.JsonParser
import com.lambda.client.LambdaMod
import com.lambda.client.commons.utils.ConnectionUtils
import com.lambda.client.gui.AbstractLambdaGui
import com.lambda.client.gui.clickgui.component.*
import com.lambda.client.gui.clickgui.window.ModuleSettingWindow
import com.lambda.client.gui.rgui.Component
import com.lambda.client.gui.rgui.windows.ListWindow
import com.lambda.client.module.AbstractModule
import com.lambda.client.module.Category
import com.lambda.client.module.ModuleManager
import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.plugin.PluginManager
import com.lambda.client.setting.ConfigManager
import com.lambda.client.util.FolderUtils
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.defaultScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.util.text.TextFormatting
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import org.lwjgl.input.Keyboard
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

object LambdaClickGui : AbstractLambdaGui<ModuleSettingWindow, AbstractModule>() {

    private val windows = ArrayList<ListWindow>()
    var pluginWindow: ListWindow
    var remotePluginWindow: ListWindow
    var disabledRemotes = ArrayList<RemotePluginButton>()
    private var moduleCount = ModuleManager.modules.size

    init {
        var posX = 0.0f

        Category.values().forEach { category ->
            val window = ListWindow(category.displayName, posX, 0.0f, 90.0f, 300.0f, Component.SettingGroup.CLICK_GUI, drawHandle = true)
            windows.add(window)

            posX += 90.0f
        }

        /* Plugins */
        pluginWindow = PluginWindow("Plugins", posX, 0.0f)
        pluginWindow.add(ImportPluginButton)
        pluginWindow.add(DownloadPluginButton)
        windows.add(pluginWindow)

        posX += 120.0f

        remotePluginWindow = PluginWindow("Remote plugins", posX, 0.0f)
        remotePluginWindow.visible = false
        windows.add(remotePluginWindow)

        windowList.addAll(windows)
    }

    override fun onDisplayed() {
        reorderModules()

        super.onDisplayed()
    }

    override fun onGuiClosed() {
        super.onGuiClosed()
        setModuleButtonVisibility { true }
        setPluginButtonVisibility { true }
        setRemotePluginButtonVisibility { true }
        remotePluginWindow.visible = false
    }

    override fun newSettingWindow(element: AbstractModule, mousePos: Vec2f): ModuleSettingWindow {
        return ModuleSettingWindow(element, mousePos.x, mousePos.y)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == ClickGUI.bind.value.key && !searching && settingWindow?.listeningChild == null) {
            ClickGUI.disable()
        } else {
            super.keyTyped(typedChar, keyCode)

            val string = typedString.replace(" ", "")

            if (string.isNotEmpty()) {
                setModuleButtonVisibility { moduleButton ->
                    moduleButton.module.name.contains(string, true)
                        || moduleButton.module.alias.any { it.contains(string, true) }
                }
                setPluginButtonVisibility { pluginButton ->
                    pluginButton.name.contains(string, true)
                }
                setRemotePluginButtonVisibility { remotePluginButton ->
                    remotePluginButton.name.contains(string, true)
                }
            } else {
                setModuleButtonVisibility { true }
                setPluginButtonVisibility { true }
                setRemotePluginButtonVisibility { true }
            }
        }
    }

    private fun setModuleButtonVisibility(function: (ModuleButton) -> Boolean) {
        windowList.filterIsInstance<ListWindow>().forEach { window ->
            window.children.filterIsInstance<ModuleButton>().forEach {
                it.visible = function(it)
            }
        }
    }

    private fun setPluginButtonVisibility(function: (PluginButton) -> Boolean) {
        pluginWindow.children.filterIsInstance<PluginButton>().forEach {
            it.visible = function(it)
        }
    }

    private fun setRemotePluginButtonVisibility(function: (RemotePluginButton) -> Boolean) {
        remotePluginWindow.children
            .filterIsInstance<RemotePluginButton>()
            .filter { it !in disabledRemotes }
            .forEach {
                it.visible = function(it)
            }
    }

    fun populateRemotePlugins() {
        defaultScope.launch {
            try {
                val repoUrl = LambdaMod.GITHUB_API + "orgs/" + LambdaMod.PLUGIN_ORG + "/repos"
                val rawJson = ConnectionUtils.requestRawJsonFrom(repoUrl) {
                    LambdaMod.LOG.error("Failed to load organisation for plugins from GitHub", it)
                    throw it
                }

                LambdaMod.LOG.info("Requesting all public plugin repos from: $repoUrl")

                val pluginRepos = JsonParser().parse(rawJson).asJsonArray

                pluginRepos.forEach { pluginRepo ->
                    val releaseUrl = pluginRepo.asJsonObject.get("releases_url").asString.replace("{/id}", "")
                    val downloadsJson = ConnectionUtils.requestRawJsonFrom(pluginRepo.asJsonObject.get("releases_url").asString.replace("{/id}", "")) {
                        LambdaMod.LOG.error("Failed to load organisation for plugins from GitHub", it)
                        throw it
                    }

                    LambdaMod.LOG.info("Requesting details about: $releaseUrl")

                    val releases = JsonParser().parse(downloadsJson).asJsonArray
                    if (releases.size() > 0) {
                        releases[0]?.let { latestRelease ->
                            latestRelease.asJsonObject.get("assets").asJsonArray[0]?.let { firstAsset ->
                                val name = pluginRepo.asJsonObject.get("name").asString
                                if (!remotePluginWindow.containsName(name)) {
                                    val remoteButton = RemotePluginButton(
                                        name,
                                        pluginRepo.asJsonObject.get("description").asString,
                                        latestRelease.asJsonObject.get("tag_name").asString.replace("v", ""),
                                        firstAsset.asJsonObject.get("browser_download_url").asString,
                                        firstAsset.asJsonObject.get("name").asString
                                    )
                                    remotePluginWindow.add(remoteButton)
                                    updateRemoteStates()
                                }
                            }
                        }
                    }
                }

                LambdaMod.LOG.info("Found remote plugins: ${pluginRepos.size()}")
            } catch (e: Exception) {
                LambdaMod.LOG.error("Failed to parse plugin json", e)
            }
        }
//        if (ConnectionUtils.api_limit_reached)
    }

    fun updatePlugins() {
        PluginManager.checkPluginLoaders(PluginManager.getLoaders()).forEach { loader ->
            if (pluginWindow.children.none { loader.name == it.name }) PluginManager.load(loader)
        }

        val loaders = PluginManager.getLoaders()
        pluginWindow.children
            .filterIsInstance<PluginButton>()
            .filter { button ->
                loaders.none { button.name == it.name && button.plugin.version == it.info.version }
            }.forEach { button ->
                pluginWindow.remove(button)
                ConfigManager.save(button.plugin.config)
                PluginManager.unload(button.plugin)
                MessageSendHelper.sendChatMessage("[Plugin Manager] ${printInfo(button.name, button.plugin.version)} removed.")

                remotePluginWindow.children
                    .filterIsInstance<RemotePluginButton>()
                    .firstOrNull {
                        it.name == button.name
                    }?.let {
                        it.visible = true
                        disabledRemotes.remove(it)
                    }
            }

        updateRemoteStates()

        if (moduleCount != ModuleManager.modules.size) {
            reorderModules()
        }
    }

    fun downloadPlugin(remotePluginButton: RemotePluginButton) {
        defaultScope.launch(Dispatchers.IO) {
            val version = remotePluginButton.version.replace("v", "")
            MessageSendHelper.sendChatMessage("[Plugin Manager] Download of ${printInfo(remotePluginButton.name, version)} started...")
            try {
                URL(remotePluginButton.downloadUrl).openStream().use { inputStream ->
                    Files.copy(
                        inputStream,
                        Paths.get("${FolderUtils.pluginFolder}/${remotePluginButton.fileName}"),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

            MessageSendHelper.sendChatMessage("[Plugin Manager] Download of ${printInfo(remotePluginButton.name, version)} finished.")
            updatePlugins()
        }
    }

    fun updateRemoteStates() {
        PluginManager.loadedPlugins.forEach { plugin ->
            remotePluginWindow.children
                .filterIsInstance<RemotePluginButton>()
                .filter { plugin.name == it.name }
                .forEach { remote ->
                    val remoteVersion = DefaultArtifactVersion(remote.version)
                    val localVersion = DefaultArtifactVersion(plugin.version)
                    when {
                        remoteVersion == localVersion -> {
                            remote.update = false
                            remote.visible = false
                            disabledRemotes.add(remote)
                        }
                        remoteVersion > localVersion -> {
                            remote.update = true
                            remote.visible = true
                            disabledRemotes.remove(remote)
                        }
                        else -> {
                            remote.update = false
                            remote.visible = false
                            disabledRemotes.add(remote)
                        }
                    }
                }
        }
    }

    fun toggleRemotePluginWindow() {
        remotePluginWindow.visible = !remotePluginWindow.visible
    }

    fun reorderModules() {
        moduleCount = ModuleManager.modules.size
        val allButtons = ModuleManager.modules
            .groupBy { it.category.displayName }
            .mapValues { (_, modules) -> modules.map { ModuleButton(it) } }

        windows.filter { window ->
            window != pluginWindow
                && window != remotePluginWindow
        }.forEach { window ->
            window.clear()
            allButtons[window.name]?.let {
                window.addAll(it.customSort())
            }
        }

        setModuleButtonVisibility { moduleButton ->
            moduleButton.module.name.contains(typedString, true)
                || moduleButton.module.alias.any { it.contains(typedString, true) }
        }
    }

    fun printInfo(name: String, version: String) =
        "${TextFormatting.GREEN}$name${TextFormatting.RESET} ${TextFormatting.GRAY}v$version${TextFormatting.RESET}"

    private fun List<ModuleButton>.customSort(): List<ModuleButton> {
        return when (ClickGUI.sortBy.value) {
            ClickGUI.SortByOptions.CUSTOM -> this.sortedByDescending { it.module.priorityForGui.value }
            ClickGUI.SortByOptions.FREQUENCY -> this.sortedByDescending { it.module.clicks.value }
            else -> this.sortedBy { it.name }
        }
    }
}