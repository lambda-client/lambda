/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.gui.menu

import com.lambda.Lambda.mc
import com.lambda.command.CommandRegistry
import com.lambda.command.LambdaCommand
import com.lambda.config.Config
import com.lambda.config.ConfigLoader
import com.lambda.config.entries.Setting
import com.lambda.gui.clickgui.ClickGuiState
import com.lambda.gui.clickgui.Reveal
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.ModuleTag
import com.lambda.util.StringUtils.levenshteinDistance
import net.minecraft.client.gui.screen.ChatScreen

/**
 * Finds modules, settings and commands by name. Choosing a result does not change anything:
 * a module or a setting is shown where it lives, see [Reveal], and a command is put into chat.
 */
internal object Search {
    sealed interface Result {
        val name: String

        /** Where the result comes from, shown beside its name */
        val origin: String

        fun choose()

        class OfModule(val module: Module) : Result {
            override val name get() = module.name
            override val origin = if (module is HudModule) "HUD element" else "${module.tag.name} module"
            override fun choose() = Reveal.show(module)
        }

        class OfSetting(val setting: Setting<*>, val config: Config) : Result {
            override val name get() = setting.name
            override val origin = (listOf(config.name) + setting.getConfigCommandPath()).dropLast(1).joinToString(" \u00bb ")
            override fun choose() = Reveal.show(config, setting)
        }

        class OfCommand(val command: LambdaCommand) : Result {
            override val name = "${CommandRegistry.prefix}${command.name}"
            override val origin = "Command"
            override fun choose() = mc.setScreen(ChatScreen("${CommandRegistry.prefix}${command.name} ", true))
        }
    }

    private const val MAX_RESULTS = 30

    // Results of a kind rank together, so a module is never buried under settings that share its name
    private const val MODULE_BONUS = 300
    private const val HUD_MODULE_BONUS = 270
    private const val COMMAND_BONUS = 200

    /** The best matches for [query], strict ones if there are any and fuzzier ones otherwise */
    fun find(query: String): List<Result> {
        if (query.isEmpty()) return emptyList()
        val needle = query.lowercase()
        return rank(needle, lenient = false).ifEmpty { rank(needle, lenient = true) }
            .sortedByDescending { it.second }
            .take(MAX_RESULTS)
            .map { it.first }
    }

    private fun rank(query: String, lenient: Boolean): List<Pair<Result, Int>> = buildList {
        fun consider(result: () -> Result, bonus: Int, vararg names: String) {
            val score = names.maxOf { score(query, it.lowercase(), lenient) }
            if (score > 0) add(result() to score + bonus)
        }

        // Debug modules and what they hold are for developers: without developer mode, which
        // is what shows their window, the search does not know them either
        fun hidden(config: Any) = config is Module && config.tag == ModuleTag.DEBUG && !ClickGuiState.developerMode

        ModuleRegistry.modules.forEach { module ->
            if (hidden(module)) return@forEach
            consider({ Result.OfModule(module) }, if (module is HudModule) HUD_MODULE_BONUS else MODULE_BONUS, module.name, module.tag.name)
        }
        CommandRegistry.commands.forEach { command ->
            consider({ Result.OfCommand(command) }, COMMAND_BONUS, command.name, *command.aliases.toTypedArray())
        }
        ConfigLoader.configCategories.forEach { category ->
            category.configs.forEach { config ->
                if (hidden(config)) return@forEach
                config.settingLayers.forEachEntry { _, single ->
                    val setting = single.entry
                    if (setting.visibility()) consider({ Result.OfSetting(setting, config) }, 0, setting.name)
                }
            }
        }
    }

    /** How well [target] matches [query], from 200 for the same word down to 0 for no match */
    private fun score(query: String, target: String, lenient: Boolean): Int {
        if (target.isEmpty()) return 0
        if (target == query) return 200
        if (target.startsWith(query)) return 100 + query.length * 50 / target.length
        if (target.contains(query)) return 50 + query.length * 40 / target.length

        // Not contained: allow a few typos, more of them in a longer query
        val distance = query.levenshteinDistance(target)
        val allowed = if (lenient) (query.length / 2).coerceIn(2, 6) else (query.length / 3).coerceIn(1, 4)
        return if (distance <= allowed) (50 - distance * 10).coerceAtLeast(1) else 0
    }
}
