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

package com.lambda.module.modules.render

import com.lambda.config.entries.Setting.Companion.onValueChange
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects

object Fullbright : Module(
    name = "Fullbright",
    description = "Makes everything brighter",
    tag = ModuleTag.RENDER,
) {
    @JvmStatic val nightVision by setting("Night Vision", false, description = "Use client-side night vision for better compatibility with shaders")
        .onValueChange { _, to -> if (isEnabled) setNightVision(to) }

    private val instance = StatusEffectInstance(StatusEffects.NIGHT_VISION, -1, 1, false, false)

    init {
        onDisable {
            if (nightVision && player.hasStatusEffect(StatusEffects.NIGHT_VISION)) setNightVision(false)
        }

        listen<TickEvent.Pre> {
            if (nightVision && !player.hasStatusEffect(StatusEffects.NIGHT_VISION)) setNightVision(true)
        }
    }

    private fun SafeContext.setNightVision(value: Boolean) {
        if (value) player.addStatusEffect(instance)
        else player.removeStatusEffect(StatusEffects.NIGHT_VISION)
    }
}
