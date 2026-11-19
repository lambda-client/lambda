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

package com.lambda.util

import com.lambda.Lambda.mc
import com.lambda.config.settings.complex.Bind
import com.lambda.context.SafeContext
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.glfwGetKey
import org.lwjgl.glfw.GLFW.glfwGetMouseButton

object InputUtils {
    private val Int.pressedOrRepeated
        get() = this == 1 || this == 2

    fun SafeContext.isKeyPressed(vararg keys: Int) =
        keys.any { glfwGetKey(mc.window.handle, it) >= GLFW_PRESS }

	fun Bind.isSatisfied(): Boolean =
		(isKeyBind || isMouseBind) &&
				(!isKeyBind || InputUtil.isKeyPressed(mc.window, key)) &&
				(!isMouseBind || glfwGetMouseButton(mc.window.handle, mouse).pressedOrRepeated) &&
				trueMods.all { InputUtil.isKeyPressed(mc.window, it.code) }

    @JvmStatic
    fun isKeyMovementRelated(key: Int): Boolean {
        val options = mc.options
        return when (key) {
            options.forwardKey.boundKey.code,
            options.backKey.boundKey.code,
            options.leftKey.boundKey.code,
            options.rightKey.boundKey.code,
            options.jumpKey.boundKey.code,
            options.sprintKey.boundKey.code,
            options.sneakKey.boundKey.code -> true
            else -> false
        }
    }
}
