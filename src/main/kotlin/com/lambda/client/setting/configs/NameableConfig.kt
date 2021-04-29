package com.lambda.client.setting.configs

import com.lambda.client.setting.settings.AbstractSetting
import com.lambda.commons.interfaces.Nameable

open class NameableConfig<T : Nameable>(
    name: String,
    filePath: String
) : AbstractConfig<T>(name, filePath) {

    override fun addSettingToConfig(owner: T, setting: AbstractSetting<*>) {
        getGroupOrPut(owner.name).addSetting(setting)
    }

    open fun getSettings(nameable: Nameable) = getGroup(nameable.name)?.getSettings() ?: emptyList()

}
