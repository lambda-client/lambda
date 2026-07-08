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

package com.lambda.module.modules.player

import com.lambda.Lambda.mc
import com.lambda.config.blocks.RotationConfig
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.LambdaScreen
import com.lambda.interaction.managers.rotating.RotationMode
import com.lambda.interaction.managers.rotating.RotationRequestBuilder.Companion.rotationRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.InputUtils
import com.lambda.util.InputUtils.isKeyPressed
import com.lambda.util.math.MathUtils.toFloatSign
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.AbstractCommandBlockScreen
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen
import net.minecraft.client.gui.screen.ingame.AnvilScreen
import net.minecraft.client.gui.screen.ingame.BookEditScreen
import org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_2
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_6
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_8
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT
import org.lwjgl.glfw.GLFW.GLFW_KEY_UP

object InventoryMove : Module(
	name = "InventoryMove",
	description = "Allows you to move with GUIs opened",
	tag = ModuleTag.PLAYER,
) {
	private val clickGui by setting("ClickGui", false)
	private val disableSneak by setting("Disable Sneak", false)
	private val arrowKeys by setting("Arrow Keys", false, "Allows rotating the players camera using the arrow keys")
	private val speed by setting("Rotation Speed", 5, 1..20, 1, unit = "°/tick") { arrowKeys }
	override val rotationConfig = RotationConfig.Instant(RotationMode.Lock)

	@JvmStatic
	val shouldMove get() = isEnabled && !mc.currentScreen.hasInputOrNull

	/**
	 * Whether the current screen has text inputs or is null
	 */
	@JvmStatic
	val Screen?.hasInputOrNull: Boolean
		get() = this is ChatScreen ||
				this is AbstractSignEditScreen ||
				this is AnvilScreen ||
				this is AbstractCommandBlockScreen ||
				(this is LambdaScreen && !clickGui) ||
				this is BookEditScreen ||
				this == null

	init {
		listen<TickEvent.Pre> {
			if (!arrowKeys || mc.currentScreen.hasInputOrNull) return@listen

			val pitch = (isKeyPressed(GLFW_KEY_DOWN, GLFW_KEY_KP_2).toFloatSign() -
					isKeyPressed(GLFW_KEY_UP, GLFW_KEY_KP_8).toFloatSign()) * speed
			val yaw = (isKeyPressed(GLFW_KEY_RIGHT, GLFW_KEY_KP_6).toFloatSign() -
					isKeyPressed(GLFW_KEY_LEFT, GLFW_KEY_KP_4).toFloatSign()) * speed

			rotationRequest { rotation(player.yaw + yaw, (player.pitch + pitch).coerceIn(-90f, 90f)) }.submit()
		}
	}

	@JvmStatic
	fun isKeyMovementRelated(key: Int) =
		if (key == mc.options.sneakKey.boundKey.code && disableSneak) false
		else InputUtils.isKeyMovementRelated(key)
}
