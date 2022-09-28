package com.lambda.client.plugin.api

import com.lambda.client.command.ClientCommand
import com.lambda.client.command.CommandManager
import com.lambda.client.commons.collections.CloseableList
import com.lambda.client.commons.interfaces.Nameable
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.ListenerManager
import com.lambda.client.gui.GuiManager
import com.lambda.client.manager.Manager
import com.lambda.client.module.ModuleManager
import com.lambda.client.plugin.PluginInfo
import com.lambda.client.setting.ConfigManager
import com.lambda.client.setting.configs.PluginConfig
import com.lambda.client.util.threads.BackgroundJob
import com.lambda.client.util.threads.BackgroundScope

/**
 * A plugin. All plugin main classes must extend this class.
 *
 * The methods onLoad and onUnload may be implemented by your
 * plugin in order to do stuff when the plugin is loaded and
 * unloaded, respectively.
 */
open class Plugin : Nameable {

    private lateinit var info: PluginInfo
    override val name: String get() = info.name
    val version: String get() = info.version
    val lambdaVersion: String get() = info.minApiVersion
    val description: String get() = info.description
    val authors: Array<String> get() = info.authors
    val requiredPlugins: Array<String> get() = info.requiredPlugins
    val url: String get() = info.url
    val mixins: Array<String> get() = info.mixins

    /**
     * Config for the plugin
     */
    val config by lazy { PluginConfig(name) }

    /**
     * The list of [Manager] the plugin will add.
     *
     * @sample com.lambda.client.manager.managers.LambdaMojiManager
     */
    val managers = CloseableList<Manager>()

    /**
     * The list of [ClientCommand] the plugin will add.
     *
     * @sample com.lambda.client.command.commands.CreditsCommand
     */
    val commands = CloseableList<ClientCommand>()

    /**
     * The list of [PluginModule] the plugin will add.
     *
     * @sample com.lambda.client.module.modules.combat.KillAura
     */
    val modules = CloseableList<PluginModule>()

    /**
     * The list of [PluginHudElement] the plugin will add.
     *
     * @sample com.lambda.client.gui.hudgui.elements.client.ModuleList
     */
    val hudElements = CloseableList<PluginHudElement>()

    /**
     * The list of [BackgroundJob] the plugin will add.
     *
     * @sample com.lambda.client.module.modules.client.CommandConfig
     */
    val bgJobs = CloseableList<BackgroundJob>()

    internal fun setInfo(infoIn: PluginInfo) {
        info = infoIn
    }

    internal fun register() {
        managers.close()
        commands.close()
        modules.close()
        hudElements.close()
        bgJobs.close()

        ConfigManager.register(config)

        managers.forEach(LambdaEventBus::subscribe)
        commands.forEach(CommandManager::register)
        modules.forEach(ModuleManager::register)
        hudElements.forEach(GuiManager::register)
        bgJobs.forEach(BackgroundScope::launchLooping)

        ConfigManager.load(config)

        modules.forEach {
            if (it.isEnabled) it.enable()
        }
    }

    internal fun unregister() {
        ConfigManager.save(config)
        ConfigManager.unregister(config)

        managers.forEach {
            LambdaEventBus.unsubscribe(it)
            ListenerManager.unregister(it)
        }
        commands.forEach {
            CommandManager.unregister(it)
            ListenerManager.unregister(it)
        }
        modules.forEach {
            ModuleManager.unregister(it)
            ListenerManager.unregister(it)
        }
        hudElements.forEach {
            GuiManager.unregister(it)
            ListenerManager.unregister(it)
        }
        bgJobs.forEach(BackgroundScope::cancel)
    }

    /**
     * Called when the plugin is loaded. Override / implement this method to
     * do something when the plugin is loaded.
     */
    open fun onLoad() {}

    /**
     * Called when the plugin is unloaded. Override / implement this method to
     * do something when the plugin is unloaded.
     */
    open fun onUnload() {}

    override fun equals(other: Any?) = this === other
        || (other is Plugin
        && name == other.name)

    override fun hashCode() = name.hashCode()

    override fun toString() = info.toString()

}