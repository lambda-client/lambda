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

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.DynamicAABB.Companion.dynamicBox
import com.lambda.graphics.renderer.esp.builders.ofBox
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.setAlpha
import com.lambda.util.world.entitySearch
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.Box
import java.awt.Color

object RenderTest : Module(
    name = "Render:shrimp:Test:canned_food:",
    description = "RenderTest",
    tag = ModuleTag.DEBUG,
) {
    private val test1 by setting("Toggle visibility", true)
    private val test21 by setting("Hallo 1", true, visibility = ::test1)
    private val test22 by setting("Hallo Slider", 1.0, 0.0..5.0, 0.5, visibility = ::test1)
    private val test23 by setting("Hallo String", "bruh", visibility = ::test1)
    private val test31 by setting("Holla huh 1", true, visibility = { !test1 })
    private val test32 by setting("Holla buh 2", true, visibility = { !test1 })

    private val outlineColor = Color(100, 150, 255).setAlpha(0.5)
    private val filledColor = outlineColor.setAlpha(0.2)

    init {
        listen<RenderEvent.DynamicESP> {
            entitySearch<LivingEntity>(8.0)
                .forEach { entity ->
                    it.renderer.ofBox(entity.dynamicBox, filledColor, outlineColor)
                }
        }

        listen<RenderEvent.StaticESP> {
            it.renderer.ofBox(Box.of(player.pos, 0.3, 0.3, 0.3), filledColor, outlineColor)
        }
    }
}
