
package com.minato.module.modules.player

import com.minato.Minato.mc
import com.minato.config.blocks.RotationConfig
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.gui.MinatoScreen
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.minato.interaction.managers.rotating.RotationMode
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.InputUtils
import com.minato.util.InputUtils.isKeyPressed
import com.minato.util.math.MathUtils.toFloatSign
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
				(this is MinatoScreen && !clickGui) ||
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
