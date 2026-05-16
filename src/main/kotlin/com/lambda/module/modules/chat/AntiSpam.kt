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

package com.lambda.module.modules.chat

import com.lambda.config.Config
import com.lambda.config.Group
import com.lambda.config.SettingBlock
import com.lambda.event.events.ChatEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.friend.FriendHandler
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.ChatUtils.addresses
import com.lambda.util.ChatUtils.colors
import com.lambda.util.ChatUtils.discord
import com.lambda.util.ChatUtils.hex
import com.lambda.util.ChatUtils.sexual
import com.lambda.util.ChatUtils.slurs
import com.lambda.util.ChatUtils.swears
import com.lambda.util.ChatUtils.toAscii
import com.lambda.util.Describable
import com.lambda.util.text.DirectMessage
import com.lambda.util.text.MessageParser
import com.lambda.util.text.MessageType
import net.minecraft.text.Text

@Suppress("unused")
object AntiSpam : Module(
	name = "AntiSpam",
	description = "Keeps your chat clean",
	tag = ModuleTag.Chat,
	modulePriority = 100
) {
	private val fancyChats by setting("Replace Fancy Chat", false)

	private val filterSelf by setting("Ignore Self", true)
	private val filterFriends by setting("Ignore Friends", true)
	private val filterSystem by setting("Filter System Messages", false)
	private val filterDms by setting("Filter DMs", true)

	private val ignoreSystem by setting("Ignore System", false)
	private val ignoreDms by setting("Ignore DMs", false)

	@Group("Slurs") private val detectSlurs by settingBlock(ReplaceSettings("Slurs", this))
	@Group("Swears") private val detectSwears by settingBlock(ReplaceSettings("Swears", this))
	@Group("Sexual") private val detectSexual by settingBlock(ReplaceSettings("Sexual", this))
	@Group("Discord") private val detectDiscord by settingBlock(ReplaceSettings("Discord", this, ActionStrategy.Hide))
	@Group("Addresses") private val detectAddresses by settingBlock(ReplaceSettings("Addresses", this, ActionStrategy.Hide))
	@Group("Hex") private val detectHexBypass by settingBlock(ReplaceSettings("Hex", this, ActionStrategy.Hide))
	@Group("Colors") private val detectColors by settingBlock(ReplaceSettings("Colors", this, ActionStrategy.None))

	init {
		listen<ChatEvent.Receive> { event ->
			var raw = event.message.string
			val author = MessageParser.playerName(raw)

			if ((ignoreSystem && !MessageType.Both.matches(raw) && !DirectMessage.Both.matches(raw)) ||
				(ignoreDms && DirectMessage.Receive.matches(raw))
				) return@listen

			val slurMatches = slurs.takeIf { detectSlurs.enabled }.orEmpty().flatMap { it.findAll(raw).toList().reversed() }
			val swearMatches = swears.takeIf { detectSwears.enabled }.orEmpty().flatMap { it.findAll(raw).toList().reversed() }
			val sexualMatches = sexual.takeIf { detectSexual.enabled }.orEmpty().flatMap { it.findAll(raw).toList().reversed() }
			val discordMatches = discord.takeIf { detectDiscord.enabled }.orEmpty().flatMap { it.findAll(raw).toList().reversed() }
			val addressMatches = addresses.takeIf { detectAddresses.enabled }.orEmpty().flatMap { it.findAll(raw).toList().reversed() }
			val hexMatches = hex.takeIf { detectHexBypass.enabled }.orEmpty().flatMap { it.findAll(raw).toList().reversed() }
			val colorMatches = colors.takeIf { detectColors.enabled }.orEmpty().flatMap { it.findAll(raw).toList().reversed() }

			var cancelled = false
			var hasMatches = false

			fun doMatch(replace: ReplaceSettings, matches: Sequence<MatchResult>) {
				if (cancelled ||
					(filterSystem && !MessageType.Both.matches(raw) && !DirectMessage.Both.matches(raw)) ||
					(filterDms && DirectMessage.Receive.matches(raw)) ||
					(filterFriends && author?.let { FriendHandler.isFriend(it) } == true) ||
					(filterSelf && MessageType.Self.matches(raw))
					) return

				when (replace.action) {
					ActionStrategy.Hide -> matches.firstOrNull()?.let { event.cancel(); cancelled = true } // If there's one detection, nuke the whole damn thang
					ActionStrategy.Delete -> matches
						.forEach { raw = raw.replaceRange(it.range, ""); hasMatches = true }
					ActionStrategy.Replace -> matches
						.forEach { raw = raw.replaceRange(it.range, replace.replace.block(it.value)); hasMatches = true }
					ActionStrategy.None -> {}
				}
			}

			doMatch(detectSlurs, slurMatches)
			doMatch(detectSwears, swearMatches)
			doMatch(detectSexual, sexualMatches)
			doMatch(detectDiscord, discordMatches)
			doMatch(detectAddresses, addressMatches)
			doMatch(detectHexBypass, hexMatches)
			doMatch(detectColors, colorMatches)

			if (cancelled) return@listen event.cancel()

			if (hasMatches) event.message = Text.of(if (fancyChats) raw.toAscii else raw)
		}
	}

	class ReplaceSettings(
		val name: String,
		override val c: Config,
		actionStrategy: ActionStrategy = ActionStrategy.Replace
	) : SettingBlock {
		val action by c.setting("$name Action Strategy", actionStrategy)
		val replace by c.setting("$name Replace Strategy", ReplaceStrategy.CensorAll) { action == ActionStrategy.Replace }

		val enabled: Boolean get() = action != ActionStrategy.None
	}

	enum class ActionStrategy(override val description: String) : Describable {
		Hide("Hides the message. Will override other strategies."),
		Delete("Deletes the matching part off the message."),
		Replace("Replace the matching string in the message with one of the following replace strategy."),
		None("Don't do anything."),
	}

	@Suppress("unused")
	enum class ReplaceStrategy(val block: (String) -> String) {
		CensorAll({ it.replaceRange(0..<it.length, "*".repeat(it.length))}),
		CensorHalf({ it.foldIndexed("") { i, acc, char -> if (i % 2 == 0) acc + char else "$acc*" } }),
		KeepFirst({ if (it.length <= 1) it else it.replaceRange(1, it.length, "*".repeat(it.length - 1)) }),
	}
}