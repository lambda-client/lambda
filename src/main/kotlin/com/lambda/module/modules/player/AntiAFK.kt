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

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import net.minecraft.util.Hand

@Suppress("unused")
object AntiAFK : Module(
    name = "AntiAFK",
    description = "Keeps you from getting kicked",
    tag = ModuleTag.PLAYER,
) {
    private val delay by setting("Delay", 300, 5..600, 1, unit = " s", description = "Delay between swinging the hand.")
    private val swingHand by setting("Swing Hand", Hand.MAIN_HAND, description = "Hand to swing.")

    init {
        listen<TickEvent.Pre> {
            if (mc.uptimeInTicks % (delay * 20) != 0L) return@listen
            player.swingHand(swingHand)
        }
    }
}
