
package com.minato.config

import com.minato.core.Loadable

object ConfigLoader: Loadable {
    val configCategories = mutableSetOf<ConfigCategory>()
    val configs: Set<Config>
        get() = configCategories.flatMapTo(mutableSetOf()) { it.configs }

    override fun load(): String {
        configCategories.forEach {
            it.tryLoadFromFile()
        }
        return "Loading ${configCategories.size} config categories"
    }

    fun configByName(name: String) =
        configs.find { it.name == name }

    fun configByCommandName(name: String) =
        configs.find { it.commandName == name }
}