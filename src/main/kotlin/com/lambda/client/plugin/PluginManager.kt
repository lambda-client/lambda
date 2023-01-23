package com.lambda.client.plugin

import com.lambda.client.AsyncLoader
import com.lambda.client.LambdaMod
import com.lambda.client.commons.collections.NameableSet
import com.lambda.client.gui.clickgui.LambdaClickGui
import com.lambda.client.gui.clickgui.component.PluginButton
import com.lambda.client.plugin.api.Plugin
import com.lambda.client.util.FolderUtils
import com.lambda.client.util.text.MessageSendHelper
import kotlinx.coroutines.Deferred
import net.minecraft.launchwrapper.Launch
import net.minecraft.util.text.TextFormatting
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import java.io.File
import java.io.FileNotFoundException
import java.util.jar.JarFile

internal object PluginManager : AsyncLoader<List<PluginLoader>> {
    override var deferred: Deferred<List<PluginLoader>>? = null

    val loadedPlugins = NameableSet<Plugin>()
    val loadedPluginLoader = NameableSet<PluginLoader>()

    private val lambdaVersion = DefaultArtifactVersion(LambdaMod.VERSION)

    override fun preLoad0() = checkPluginLoaders(getLoaders())

    override fun load0(input: List<PluginLoader>) {
        loadAll(input)
    }

    fun getLoaders(): List<PluginLoader> {
        val dir = File(FolderUtils.pluginFolder)
        if (!dir.exists()) dir.mkdir()

        val files = dir.listFiles() ?: return emptyList()
        val jarFiles = files.filter { it.extension.equals("jar", true) }
        val plugins = ArrayList<PluginLoader>()

        jarFiles.forEach {
            try {
                val loader = PluginLoader(it)
                loader.verify()
                plugins.add(loader)
            } catch (e: FileNotFoundException) {
                LambdaMod.LOG.info("${it.name} is not a valid plugin. Skipping...")
            } catch (e: PluginInfoMissingException) {
                LambdaMod.LOG.warn("${it.name} is missing a required info ${e.infoName}. Skipping...", e)
            } catch (e: Exception) {
                LambdaMod.LOG.error("Failed to pre load plugin ${it.name}", e)
            }
        }

        return plugins
    }

    fun loadAll(loaders: List<PluginLoader>) {
        val validLoaders = checkPluginLoaders(loaders)

        synchronized(this) {
            validLoaders.forEach(PluginManager::loadWithoutCheck)
        }

        LambdaMod.LOG.info("Loaded ${loadedPlugins.size} plugins!")
    }

    fun checkPluginLoaders(loaders: List<PluginLoader>): List<PluginLoader> {
        val loaderSet = NameableSet<PluginLoader>()
        val invalids = HashSet<PluginLoader>()

        for (loader in loaders) {
            // Hot reload check, the error shouldn't be show when reload in game
            if (LambdaMod.ready && loader.info.mixins.isNotEmpty()) {
                invalids.add(loader)
            }

            // Unsupported check
            if (DefaultArtifactVersion(loader.info.minApiVersion) > lambdaVersion) {
                PluginError.UNSUPPORTED.handleError(loader)
                invalids.add(loader)
            }

            if (invalids.contains(loader)) continue

            // Duplicate check
            if (loadedPluginLoader.contains(loader)) {
                loadedPlugins.firstOrNull { loader.name == it.name }?.let { plugin ->
                    val loadingVersion = DefaultArtifactVersion(loader.info.version)
                    val loadedVersion = DefaultArtifactVersion(plugin.version)
                    if (loadingVersion > loadedVersion) {
                        MessageSendHelper.sendChatMessage("[Plugin Manager] Updating ${TextFormatting.GREEN}${loader.name}${TextFormatting.RESET} from ${TextFormatting.GRAY}$loadedVersion${TextFormatting.RESET} to ${TextFormatting.GRAY}$loadingVersion")
                        unload(plugin)
                        LambdaClickGui.pluginWindow.children.firstOrNull { plugin.name == it.name }?.let {
                            LambdaClickGui.pluginWindow.remove(it)
                        }
                    } else {
                        invalids.add(loader)
                    }
                }
            } else {
                var upgradeLoader = false
                loaderSet[loader.name]?.let {
                    // Choose latest plugin
                    val nowVersion = DefaultArtifactVersion(loader.info.version)
                    val thenVersion = DefaultArtifactVersion(it.info.version)
                    when {
                        nowVersion == thenVersion -> {
                            PluginError.DUPLICATE.handleError(loader)
                            invalids.add(loader)
                            PluginError.DUPLICATE.handleError(it)
                            invalids.add(it)
                        }

                        nowVersion > thenVersion -> {
                            upgradeLoader = true
                            PluginError.DEPRECATED.handleError(it, loader.toString())
                            invalids.add(it)
                        }

                        else -> {
                            PluginError.DEPRECATED.handleError(loader, it.toString())
                            invalids.add(loader)
                        }
                    }
                } ?: run {
                    loaderSet.add(loader)
                }
                if (upgradeLoader) {
                    loaderSet.remove(loader)
                    loaderSet.add(loader)
                }
            }
        }

        // Required plugin check
        loaders.filter {
            !loadedPlugins.containsNames(it.info.requiredPlugins)
                && !loaderSet.containsNames(it.info.requiredPlugins)
        }.forEach {
            PluginError.REQUIRED_PLUGIN.handleError(it)
            invalids.add(it)
        }

        return loaders.filter { !invalids.contains(it) }
    }

    fun load(loader: PluginLoader) {
        synchronized(this) {
            val hotReload = LambdaMod.ready && loader.info.mixins.isNotEmpty()
            val duplicate = loadedPlugins.containsName(loader.name)
            val unsupported = DefaultArtifactVersion(loader.info.minApiVersion) > lambdaVersion
            val missing = !loadedPlugins.containsNames(loader.info.requiredPlugins)

            if (hotReload) PluginError.HOT_RELOAD.handleError(loader)
            if (duplicate) PluginError.DUPLICATE.handleError(loader)
            if (unsupported) PluginError.UNSUPPORTED.handleError(loader)
            if (missing) PluginError.REQUIRED_PLUGIN.handleError(loader)

            if (hotReload || duplicate || unsupported || missing) return

            loadWithoutCheck(loader)
        }
    }

    private fun loadWithoutCheck(loader: PluginLoader) {
        val plugin = synchronized(this) {
            val plugin = runCatching(loader::load).getOrElse {
                when (it) {
                    is ClassNotFoundException -> {
                        PluginError.CLASS_NOT_FOUND.handleError(loader, throwable = it)
                    }

                    is IllegalAccessException -> {
                        PluginError.ILLEGAL_ACCESS.handleError(loader, throwable = it)
                    }

                    else -> {
                        PluginError.OTHERS.handleError(loader, throwable = it)
                    }
                }
                return
            }

            try {
                plugin.onLoad()
            } catch (e: NoSuchFieldError) {
                PluginError.MISSING_DEFINITION.handleError(loader, throwable = e)
                return
            } catch (e: NoSuchMethodError) {
                if (e.message?.contains("getModules()Lcom") == true) {
                    PluginError.OUTDATED_PLUGIN.handleError(loader)
                } else {
                    PluginError.MISSING_DEFINITION.handleError(loader, throwable = e)
                }
                return
            } catch (e: NoClassDefFoundError) {
                PluginError.MISSING_DEFINITION.handleError(loader, throwable = e)
                return
            }

            plugin.register()
            loadedPlugins.add(plugin)

            if (!LambdaClickGui.pluginWindow.containsName(loader.name)) {
                LambdaClickGui.pluginWindow.children.add(PluginButton(plugin, loader.file))
            }

            LambdaClickGui.updateRemoteStates()
            loadedPluginLoader.add(loader)
            plugin
        }

        LambdaMod.LOG.info("Loaded plugin ${plugin.name} v${plugin.version}")
        MessageSendHelper.sendChatMessage("[Plugin Manager] ${LambdaClickGui.printInfo(plugin.name, plugin.version)} loaded.")
    }

    fun unloadAll() {
        loadedPlugins.filter { it.mixins.isEmpty() }.forEach(PluginManager::unloadWithoutCheck)

        LambdaMod.LOG.info("Unloaded all plugins!")
    }

    fun unload(plugin: Plugin): Boolean {
        if (loadedPlugins.any { it.requiredPlugins.contains(plugin.name) }) {
            MessageSendHelper.sendErrorMessage("Plugin ${plugin.name} is required by another plugin!")
        }

        return unloadWithoutCheck(plugin)
    }

    private fun unloadWithoutCheck(plugin: Plugin): Boolean {
        // Necessary because of plugin GUI
        if (plugin.mixins.isNotEmpty()) {
            MessageSendHelper.sendErrorMessage("Plugin ${plugin.name} cannot be hot reloaded because of contained mixins.")
            return false
        }

        synchronized(this) {
            if (loadedPlugins.remove(plugin)) {
                plugin.modules.forEach { it.disable() }
                plugin.unregister()
                plugin.onUnload()
                loadedPluginLoader[plugin.name]?.let {
                    it.close()
                    loadedPluginLoader.remove(it)
                }
            }
        }

        LambdaMod.LOG.info("Unloaded plugin ${plugin.name} v${plugin.version}")
        MessageSendHelper.sendChatMessage("[Plugin Manager] ${LambdaClickGui.printInfo(plugin.name, plugin.version)} unloaded.")
        return true
    }
}