/*
 * Copyright 2025 Lambda
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

package com.lambda.module.modules.chat

import com.lambda.config.Configurable
import com.lambda.config.SettingGroup
import com.lambda.config.applyEdits
import com.lambda.config.groups.ReplaceConfig
import com.lambda.event.events.ChatEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.friend.FriendManager
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.ChatUtils.addresses
import com.lambda.util.ChatUtils.colors
import com.lambda.util.ChatUtils.discord
import com.lambda.util.ChatUtils.sexual
import com.lambda.util.ChatUtils.slurs
import com.lambda.util.ChatUtils.swears
import com.lambda.util.ChatUtils.toAscii
import com.lambda.util.NamedEnum
import net.minecraft.text.Text

object AntiSpam : Module(
	name = "AntiSpam",
	description = "Keeps your chat clean",
	tag = ModuleTag.CHAT,
) {
	private val ignoreSelf by setting("Ignore Self", true)
	private val ignoreFriends by setting("Ignore Friends", true)
	private val fancyChats by setting("Replace Fancy Chat", false)

	private val detectSlurs = ReplaceSettings("Slurs", this, Group.Slurs)
	private val detectSwears = ReplaceSettings("Swears", this, Group.Swears)
	private val detectSexual = ReplaceSettings("Sexual", this, Group.Sexual)
	private val detectDiscord = ReplaceSettings("Discord", this, Group.Discord)
		.apply { applyEdits { editTyped(::action) { defaultValue(ReplaceConfig.ActionStrategy.Hide) } } }
	private val detectAddresses = ReplaceSettings("Addresses", this, Group.Addresses)
		.apply { applyEdits { editTyped(::action) { defaultValue(ReplaceConfig.ActionStrategy.Hide) } } }
	private val detectColors = ReplaceSettings("Colors", this, Group.Colors)
		.apply { applyEdits { editTyped(::action) { defaultValue(ReplaceConfig.ActionStrategy.None) } } }

	enum class Group(override val displayName: String) : NamedEnum {
		General("General"),
		Slurs("Slurs"),
		Swears("Swears"),
		Sexual("Sexual"),
		Discord("Discord Invites"),
		Addresses("IPs and Addresses"),
		Colors("Color Prefixes")
	}

	init {
		listen<ChatEvent.Message> { event ->
			val author = event.message.string.substringAfter('<').substringBefore('>')
			var content = event.message.string.substringAfter(' ')

			if (!ignoreFriends && FriendManager.isFriend(author) ||
				!ignoreSelf && player.gameProfile.name == author) return@listen

			val slurMatches = slurs.takeIf { detectSlurs.enabled }.orEmpty()
				.flatMap { it.findAll(content).toList().reversed() }

			val swearMatches = swears.takeIf { detectSwears.enabled }.orEmpty()
				.flatMap { it.findAll(content).toList().reversed() }

			val sexualMatches = sexual.takeIf { detectSexual.enabled }.orEmpty()
				.flatMap { it.findAll(content).toList().reversed() }

			val discordMatches = discord.takeIf { detectDiscord.enabled }.orEmpty()
				.flatMap { it.findAll(content).toList().reversed() }

			val addressMatches = addresses.takeIf { detectAddresses.enabled }.orEmpty()
				.flatMap { it.findAll(content).toList().reversed() }

			val colorMatches = colors.takeIf { detectColors.enabled }.orEmpty()
				.flatMap { it.findAll(content).toList().reversed() }

			var cancelled = false
			var hasMatches = false

			fun doMatch(replace: ReplaceConfig, matches: Sequence<MatchResult>) {
				if (cancelled) return

				when (replace.action) {
					ReplaceConfig.ActionStrategy.Hide -> matches.firstOrNull()?.let { event.cancel(); cancelled = true } // If there's one detection, nuke the whole damn thang
					ReplaceConfig.ActionStrategy.Delete -> matches
						.forEach { content = content.replaceRange(it.range, ""); hasMatches = true }
					ReplaceConfig.ActionStrategy.Replace -> matches
						.forEach { content = content.replaceRange(it.range, replace.replace.block(it.value)); hasMatches = true }
					ReplaceConfig.ActionStrategy.None -> {}
				}
			}

			doMatch(detectSlurs, slurMatches)
			doMatch(detectSwears, swearMatches)
			doMatch(detectSexual, sexualMatches)
			doMatch(detectDiscord, discordMatches)
			doMatch(detectAddresses, addressMatches)
			doMatch(detectColors, colorMatches)

			if (!hasMatches) return@listen

			val postprocessed = if (fancyChats) content.toAscii else content
			event.message = Text.of("<$author> $postprocessed")
		}
	}

	class ReplaceSettings(
		name: String,
		c: Configurable,
		baseGroup: NamedEnum,
	) : ReplaceConfig, SettingGroup(c) {
		override val action by setting("$name Action Strategy", ReplaceConfig.ActionStrategy.Replace).group(baseGroup)
		override val replace by setting("$name Replace Strategy", ReplaceConfig.ReplaceStrategy.CensorAll) { action == ReplaceConfig.ActionStrategy.Replace }.group(baseGroup)
	}
}