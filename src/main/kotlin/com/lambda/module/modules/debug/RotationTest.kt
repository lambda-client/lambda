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

import com.lambda.config.AutomationConfig
import com.lambda.config.groups.RotationSettings
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.RotationRequest
import com.lambda.interaction.managers.rotating.visibilty.lookAt
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.util.hit.HitResult

object RotationTest : Module(
    name = "RotationTest",
    tag = ModuleTag.DEBUG,
) {
    override val rotationConfig = RotationSettings(this, AutomationConfig.Group.Rotation)
    var hitPos: HitResult? = null
    
    init {
        onEnable {
            hitPos = mc.crosshairTarget
        }

        listen<TickEvent.Pre> {
            hitPos?.let { RotationRequest(lookAt(it.pos), this@RotationTest).submit() }
        }
    }
}
