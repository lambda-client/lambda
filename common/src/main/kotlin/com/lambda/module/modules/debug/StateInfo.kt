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

package com.lambda.module.modules.debug

import com.lambda.event.events.KeyboardEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.info
import com.lambda.util.KeyCode
import net.minecraft.util.hit.BlockHitResult

object StateInfo : Module(
    "State Info",
    "Prints the target block's state into chat",
    setOf(ModuleTag.DEBUG)
) {
    private val printBind by setting("Print", KeyCode.UNBOUND, "The bind used to print the info to chat")

    init {
        listen<KeyboardEvent.Press> { event ->
            if (!event.isPressed) return@listen
            if (event.keyCode != printBind.keyCode) return@listen
            val crosshair = mc.crosshairTarget ?: return@listen
            if (crosshair !is BlockHitResult) return@listen

            val targetBlock = blockState(crosshair.blockPos)
            val text = "$targetBlock"
            info(text)
        }
    }
}