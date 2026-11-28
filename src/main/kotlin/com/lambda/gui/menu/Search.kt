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
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.automation.UserAutomationConfig
import com.lambda.config.entries.Setting
import com.lambda.gui.GuiScreen
import com.lambda.gui.Reveal
import com.lambda.gui.clickgui.ClickGuiState
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.ModuleTag
import com.lambda.util.StringUtils.levenshteinDistance
import net.minecraft.client.gui.screen.ChatScreen

internal object Search {
	sealed interface Result {
		val name: String

		val origin: String

		fun choose()

		class OfModule(val module: Module) : Result {
			override val name get() = module.name
			override val origin =
				if (module is HudModule) "HUD element"
				else "${module.tag.name} module"
			override fun choose() = Reveal.show(module)
		}

		class OfSetting(
			val setting: Setting<*>,
			val config: Config,
			path: List<String>
		) : Result {
			override val name get() = setting.name
			override val origin = (listOf(config.name) + path).joinToString(" \u00bb ")
			override fun choose() = Reveal.show(config, setting)
		}

		class OfCommand(val command: LambdaCommand) : Result {
			override val name = "${CommandRegistry.prefix}${command.name}"
			override val origin = "Command"
			override fun choose() = mc.setScreen(ChatScreen("${CommandRegistry.prefix}${command.name} ", true))
		}
	}

	private const val MODULE_BONUS = 300
	private const val HUD_MODULE_BONUS = 270
	private const val COMMAND_BONUS = 200

	private const val OWN_PLACE = 0
	private const val SHARED_AUTOMATION_PLACE = 1
	private const val MODULE_AUTOMATION_PLACE = 2

	private val chatReachable get() = mc.world != null && !GuiScreen.isOverScreen

	fun find(query: String): List<Result> {
		if (query.isEmpty()) return emptyList()
		val needle = query.lowercase()
		return rank(needle, lenient = false).ifEmpty { rank(needle, lenient = true) }
			.sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.place })
			.map { it.result }
	}

	private class Ranked(val result: Result, val score: Int, val place: Int)

	private fun place(config: Config) =
		when {
			config !is AutomationConfig -> OWN_PLACE
			config === AutomationConfig.DEFAULT || config is UserAutomationConfig -> SHARED_AUTOMATION_PLACE
			else -> MODULE_AUTOMATION_PLACE
		}

	private fun rank(query: String, lenient: Boolean): List<Ranked> =
		buildList {
			fun consider(result: () -> Result, bonus: Int, place: Int, vararg names: String) {
				val score = names.maxOf { score(query, it.lowercase(), lenient) }
				if (score > 0) add(Ranked(result(), score + bonus, place))
			}

			fun hidden(config: Any) =
				config is Module &&
						config.tag == ModuleTag.DEBUG &&
						!ClickGuiState.developerMode

			ModuleRegistry.modules.forEach { module ->
				if (hidden(module)) return@forEach
				consider(
					{ Result.OfModule(module) },
					if (module is HudModule) HUD_MODULE_BONUS else MODULE_BONUS,
					OWN_PLACE,
					module.name,
					module.tag.name
				)
			}
			if (chatReachable) {
				CommandRegistry.commands.forEach { command ->
					consider(
						{ Result.OfCommand(command) },
						COMMAND_BONUS,
						OWN_PLACE,
						command.name,
						*command.aliases.toTypedArray()
					)
				}
			}
			ConfigLoader.configCategories.forEach { category ->
				category.configs.forEach { config ->
					if (hidden(config)) return@forEach
					config.settingLayers.forEachEntry { path, single ->
						val setting = single.entry
						if (setting.visibility()) {
							consider(
								{
									Result.OfSetting(
										setting,
										config,
										path.map { it.name }
									)
								},
								0,
								place(config),
								setting.name
							)
						}
					}
				}
			}
		}

	private fun score(query: String, target: String, lenient: Boolean): Int {
		if (target.isEmpty()) return 0
		if (target == query) return 200
		if (target.startsWith(query)) return 100 + (query.length * 50 / target.length)
		if (target.contains(query)) return 50 + (query.length * 40 / target.length)

		val distance = query.levenshteinDistance(target)
		val allowed =
			if (lenient) (query.length / 2).coerceIn(2, 6)
			else (query.length / 3).coerceIn(1, 4)
		return if (distance <= allowed) (50 - (distance * 10)).coerceAtLeast(1)
		else 0
	}
}
