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

package com.lambda.module.modules.player

import com.lambda.event.events.MovementEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.LambdaScreen
import com.lambda.interaction.request.rotating.Rotation
import com.lambda.interaction.request.rotating.RotationConfig
import com.lambda.interaction.request.rotating.RotationManager.onRotate
import com.lambda.interaction.request.rotating.RotationMode
import com.lambda.interaction.request.rotating.visibilty.lookAt
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyboardUtils.isKeyPressed
import com.lambda.util.math.MathUtils.toDouble
import com.lambda.util.math.MathUtils.toFloatSign
import com.lambda.util.player.MovementUtils.buildMovementInput
import com.lambda.util.player.MovementUtils.mergeFrom
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.AnvilScreen
import net.minecraft.client.gui.screen.ingame.CommandBlockScreen
import net.minecraft.client.gui.screen.ingame.SignEditScreen
import org.lwjgl.glfw.GLFW.GLFW_KEY_A
import org.lwjgl.glfw.GLFW.GLFW_KEY_D
import org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_2
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_6
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_8
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT
import org.lwjgl.glfw.GLFW.GLFW_KEY_S
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_UP
import org.lwjgl.glfw.GLFW.GLFW_KEY_W

object InventoryMove : Module(
    name = "InventoryMove",
    description = "Allows you to move with GUIs opened",
    defaultTags = setOf(ModuleTag.PLAYER, ModuleTag.MOVEMENT)
) {
    private val speed by setting("Rotation Speed", 5, 1..20, 1, unit = "°/tick")
    private val rotationConfig = RotationConfig.Instant(RotationMode.Lock)

    /**
     * Whether the current screen has text inputs or is null
     */
    val Screen?.hasInputOrNull: Boolean
        get() = this is ChatScreen ||
                this is SignEditScreen ||
                this is AnvilScreen ||
                this is CommandBlockScreen ||
                this is LambdaScreen ||
                this == null

    init {
        listen<MovementEvent.InputUpdate>(20250415) { event ->
            if (mc.currentScreen.hasInputOrNull) return@listen

            val forward = isKeyPressed(GLFW_KEY_W).toDouble() -
                    isKeyPressed(GLFW_KEY_S).toDouble()

            val strafe = isKeyPressed(GLFW_KEY_A).toDouble() -
                    isKeyPressed(GLFW_KEY_D).toDouble()

            val jump = isKeyPressed(GLFW_KEY_SPACE)
            val sneak = isKeyPressed(GLFW_KEY_LEFT_SHIFT)

            player.isSprinting = isKeyPressed(GLFW_KEY_LEFT_CONTROL)
            event.input.mergeFrom(buildMovementInput(forward, strafe, jump, sneak))
        }

        onRotate {
            if (mc.currentScreen.hasInputOrNull) return@onRotate

            val pitch = (isKeyPressed(GLFW_KEY_DOWN, GLFW_KEY_KP_2).toFloatSign() -
                    isKeyPressed(GLFW_KEY_UP, GLFW_KEY_KP_8).toFloatSign()) * speed
            val yaw = (isKeyPressed(GLFW_KEY_RIGHT, GLFW_KEY_KP_6).toFloatSign() -
                    isKeyPressed(GLFW_KEY_LEFT, GLFW_KEY_KP_4).toFloatSign()) * speed

            lookAt(
                Rotation(player.yaw + yaw, (player.pitch + pitch).coerceIn(-90f, 90f))
            ).requestBy(rotationConfig)
        }
    }
}
