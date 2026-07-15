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

package com.lambda.module.modules.debug

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.manager.managers.rotating.RotationRequestBuilder.Companion.rotationRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.player.RotationUtils.lookAt
import net.minecraft.util.hit.HitResult

@Suppress("unused")
object RotationTest : Module(
    name = "RotationTest",
    tag = ModuleTag.DEBUG,
) {
    var hitPos: HitResult? = null
    
    init {
        onEnable {
            hitPos = mc.crosshairTarget
        }

        listen<TickEvent.Pre> {
            hitPos?.let { rotationRequest { rotation(lookAt(it.pos)) }.submit() }
        }
    }
}
