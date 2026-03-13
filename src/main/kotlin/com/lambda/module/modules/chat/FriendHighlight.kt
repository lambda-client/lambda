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

import com.lambda.event.events.ChatEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.friend.FriendManager
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.sound.SoundManager.playSound
import com.lambda.util.Communication.logError
import com.lambda.util.text.MessageType
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import com.lambda.util.text.styled
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Formatting
import java.awt.Color

object FriendHighlight : Module(
	name = "FriendHighlight",
	description = "Highlights your friends names in chat",
	tag = ModuleTag.CHAT,
) {
	var color: Formatting by setting("Color", Formatting.GREEN)
		.onValueChange { from, to -> if (to.colorIndex !in 0..15) color = from }

	val javaColor: Color get() = Color(color.colorValue!! and 16777215)

	val bold by setting("Bold", true)
	val italic by setting("Italic", false)
	val underlined by setting("Underlined", false)
	val strikethrough by setting("Strikethrough", false)

	val ping by setting("Ping On Message", true)

	init {
		onEnable {
			if (FriendManager.friends.isEmpty())
				logError("You don't have any friends added, silly! Go add some friends before using the module")
		}

		listen<ChatEvent.Receive> {
			val raw = it.message.string
			val author = MessageType.Others.playerName(raw) ?: return@listen
			val content = MessageType.Others.removedOrNull(raw) ?: return@listen

			if (!FriendManager.isFriend(author)) return@listen

			if (ping) playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP)

			it.message = buildText {
				literal("<")
				styled(javaColor, bold, italic, underlined, strikethrough) { literal(author) }
				literal(">")

				literal(content.toString())
			}
		}
	}
}