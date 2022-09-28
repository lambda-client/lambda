package com.lambda.client.setting.configs

import com.lambda.client.commons.interfaces.Nameable
import com.lambda.client.setting.settings.AbstractSetting

open class NameableConfig<T : Nameable>(
    name: String,
    filePath: String
) : AbstractConfig<T>(name, filePath) {

    override fun addSettingToConfig(owner: T, setting: AbstractSetting<*>) {
        getGroupOrPut(owner.name).addSetting(setting)
    }

    override fun getSettings(owner: T) = getGroup(owner.name)?.getSettings() ?: emptyList()
}
