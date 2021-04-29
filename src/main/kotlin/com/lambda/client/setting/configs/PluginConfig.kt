package com.lambda.client.setting.configs

import com.lambda.client.plugin.api.IPluginClass
import com.lambda.client.plugin.api.PluginHudElement
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.setting.settings.AbstractSetting
import com.lambda.commons.interfaces.Nameable
import java.io.File

class PluginConfig(pluginName: String) : NameableConfig<IPluginClass>(
    pluginName, "${com.lambda.client.LambdaMod.DIRECTORY}config/plugins/$pluginName"
) {
    override val file: File get() = File("$filePath/default.json")
    override val backup: File get() = File("$filePath/default.bak")

    override fun addSettingToConfig(owner: IPluginClass, setting: AbstractSetting<*>) {
        when (owner) {
            is PluginModule -> {
                getGroupOrPut("modules").getGroupOrPut(owner.name).addSetting(setting)
            }
            is PluginHudElement -> {
                getGroupOrPut("hud").getGroupOrPut(owner.name).addSetting(setting)
            }
            else -> {
                getGroupOrPut("misc").getGroupOrPut(owner.name).addSetting(setting)
            }
        }
    }

    override fun getSettings(nameable: Nameable): List<AbstractSetting<*>> {
        return when (nameable) {
            is PluginModule -> {
                getGroup("modules")?.getGroupOrPut(nameable.name)?.getSettings()
            }
            is PluginHudElement -> {
                getGroup("hud")?.getGroup(nameable.name)?.getSettings()
            }
            else -> {
                getGroup("misc")?.getGroup(nameable.name)?.getSettings()
            }
        } ?: emptyList()
    }
}