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

package com.lambda.module.modules.world

import com.ibm.icu.util.Calendar
import com.lambda.event.events.GuiEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.threading.runConcurrent
import com.lambda.threading.runSafeGameScheduled
import com.lambda.util.PacketUtils.sendPacket
import kotlinx.coroutines.delay
import net.minecraft.block.entity.HangingSignBlockEntity
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen
import net.minecraft.client.gui.screen.ingame.HangingSignEditScreen
import net.minecraft.client.gui.screen.ingame.SignEditScreen
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket
import java.util.*

@Suppress("unused")
object AutoSign : Module(
	name = "AutoSign",
	description = """Auto fills signs with customizable text. Leave lines empty to skip them. Supports data formatting with:
		|<d> - Day of month (1-31)
		|<dd> - Day of month (01-31)
		|<M> - Month (1-12)
        |<MM> - Month (01-12)
		|<MMM> - Month (short name, e.g., Jan)
        |<MMMM> - Month (full name, e.g., January)
		|<yy> - Year (last two digits, e.g., 26)
		|<yyyy> - Year (e.g., 2026)
        |<HH> - Hour (00-23)
		|<mm> - Minute (00-59)
        |<ss> - Second (00-59)
	""".trimMargin(),
	tag = ModuleTag.WORLD
) {
	private var autoWrite by setting("Auto Write", true)
	private var line1 by setting("Line 1", "Welcome to Lambda!") { autoWrite }
	private var line2 by setting("Line 2", "Enjoy your stay.") { autoWrite }
	private var line3 by setting("Line 3", "Have fun!") { autoWrite }
	private var line4 by setting("Line 4", "Lambda <dd>/<M>/<yy>") { autoWrite }
	private var writeOnFront by setting("Write Front", true, description = "Write on front side of the sign") { autoWrite }

	private var autoClose by setting("Auto Close", true)
	var signWriteDelay by setting("Sign Write Delay", 400L, 100L..1000L, 50L, description = "Delay in milliseconds before sending the sign text to the server") { autoClose }

	init {
		listen<GuiEvent.SignEditorOpen> { event ->
			val lines = Array(4) { i -> event.component1().frontText.getMessages(false)[i].string }
			if (autoWrite) {
				var formatLines = arrayOf(line1, line2, line3, line4)
				val calendar = Calendar.getInstance()
				val month = calendar.get(Calendar.MONTH) + 1 // Months are 0-based in Calendar

				(0 until 4).forEach { i ->
					val formattedLine = formatLines[i]
						.replace("<dd>", String.format($$"%1$td", Date()))
						.replace("<d>", String.format($$"%1$te", Date()))
						.replace("<M>", month.toString())
						.replace("<MM>", String.format("%02d", month))
						.replace("<MMM>", String.format($$"%1$tb", Date()))
						.replace("<MMMM>", String.format($$"%1$tB", Date()))
						.replace("<yy>", String.format($$"%1$ty", Date()))
						.replace("<yyyy>", String.format($$"%1$tY", Date()))
						.replace("<HH>", String.format($$"%1$tH", Date()))
						.replace("<mm>", String.format($$"%1$tM", Date()))
						.replace("<ss>", String.format($$"%1$tS", Date()))

					if (formattedLine.isNotEmpty()) lines[i] = String.format(formattedLine, Date())
				}
			}

			var editor: AbstractSignEditScreen = if (event.sign is HangingSignBlockEntity) HangingSignEditScreen(event.sign, event.front, mc.shouldFilterText())
			else SignEditScreen(event.sign, event.front, mc.shouldFilterText())
			(0 until 4).forEach { i -> editor.messages[i] = lines[i] }
			if (autoClose) {
				val pos = event.sign.pos
				val messages = editor.messages.copyOf()
				runConcurrent {
					delay(signWriteDelay)
					runSafeGameScheduled {
						connection.sendPacket {
							UpdateSignC2SPacket(
								pos,
								writeOnFront,
								messages[0], messages[1], messages[2], messages[3]
							)
						}
					}
				}
			} else mc.setScreen(editor)
			event.cancel()
		}
	}
}