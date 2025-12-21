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
import com.lambda.config.groups.ReplaceConfig
import com.lambda.event.events.ChatEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum
import net.minecraft.text.Text

object AntiSpam : Module(
	name = "AntiSpam",
	description = "Keeps your chat clean",
	tag = ModuleTag.CHAT,
) {
	private val detectSlurs = ReplaceSettings("Slurs", this, Group.Slurs)
	private val detectSwears = ReplaceSettings("Swears", this, Group.Swears)
	private val detectSexual = ReplaceSettings("Sexual", this, Group.Sexual)
	private val detectAddresses = ReplaceSettings("Addresses", this, Group.Addresses)

	val slurs = sequenceOf("\\bch[i1l]nks?\\b", "\\bc[o0]{2}ns?\\b", "f[a@4](g{1,2}|qq)([e3il1o0]t{1,2}(ry|r[i1l]e)?)?\\b", "\\bk[il1y]k[e3](ry|r[i1l]e)?s?\\b", "\\b(s[a4]nd)?n[ila4o10][gq]{1,2}(l[e3]t|[e3]r|[a4]|n[o0]g)?s?\\b", "\\btr[a4]n{1,2}([il1][e3]|y|[e3]r)s?\\b").map { Regex(it) }
	val swears = sequenceOf("fuck(er)?", "shit", "cunt", "puss(ie|y)", "bitch", "twat").map { Regex(it) }
	val sexual = sequenceOf("^cum[s\\$]?$", "cumm?[i1]ng", "h[o0]rny", "mast(e|ur)b(8|ait|ate)").map { Regex(it) }
	val addresses = sequenceOf("^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(\\.)){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$", "^(\\:\\:)?[0-9a-fA-F]{1,4}(\\:\\:?[0-9a-fA-F]{1,4}){0,7}(\\:\\:)?$", "[A-Za-z0-9-]{1,63}\\.[A-Za-z]{2,6}$").map { Regex(it) }

	enum class Group(override val displayName: String) : NamedEnum {
		Slurs("Slurs"),
		Swears("Swears"),
		Sexual("Sexual"),
		Addresses("IPs and Addresses"),
	}

	init {
		listen<ChatEvent.Message> { event ->
			val author = event.message.string.substringBefore(' ')
			var content = event.message.string.substringAfter(' ')

			val slurMatches = slurs.takeIf { detectSlurs.enabled }.orEmpty()
				.flatMap { it.findAll(content).toList().reversed() }

			val swearMatches = swears.takeIf { detectSwears.enabled }.orEmpty()
				.flatMap { it.findAll(content).toList().reversed() }

			val sexualMatches = sexual.takeIf { detectSexual.enabled }.orEmpty()
				.flatMap { it.findAll(content).toList().reversed() }

			val addressMatches = addresses.takeIf { detectAddresses.enabled }.orEmpty()
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
			doMatch(detectAddresses, addressMatches)

			if (!hasMatches) return@listen

			event.message = Text.of("$author $content")
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