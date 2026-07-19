
package com.minato.module.modules.chat

import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.event.events.ChatEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.handlers.FriendHandler
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.sound.SoundHandler.playSound
import com.minato.util.CommunicationUtils.logError
import com.minato.util.text.MessageType
import com.minato.util.text.buildText
import com.minato.util.text.literal
import com.minato.util.text.styled
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Formatting
import java.awt.Color

@Suppress("unused")
object FriendHighlight : Module(
	name = "FriendHighlight",
	description = "Highlights your friends names in chat",
	tag = ModuleTag.CHAT,
) {
	private var color: Formatting by setting("Color", Formatting.GREEN)
		.onValueChange { from, to -> if (to.colorIndex !in 0..15) color = from }
	private val javaColor: Color get() = Color(color.colorValue!! and 16777215)

	private val bold by setting("Bold", true)
	private val italic by setting("Italic", false)
	private val underlined by setting("Underlined", false)
	private val strikethrough by setting("Strikethrough", false)

	private val ping by setting("Ping On Message", true)

	init {
		onEnable {
			if (FriendHandler.friends.isEmpty())
				logError("You don't have any friends added, silly! Go add some friends before using the module")
		}

		listen<ChatEvent.Receive> {
			val raw = it.message.string
			val author = MessageType.Others.playerName(raw) ?: return@listen
			val content = MessageType.Others.removedOrNull(raw) ?: return@listen

			if (!FriendHandler.isFriend(author)) return@listen

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