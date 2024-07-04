package com.lambda.module.modules.debug

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.DynamicAABB.Companion.dynamicBox
import com.lambda.graphics.renderer.esp.builders.build
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.world.WorldUtils.getClosestEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.Box
import java.awt.Color

object RenderTest : Module(
    name = "Render:shrimp:Test:canned_food:",
    description = "RenderTest",
    defaultTags = setOf(ModuleTag.DEBUG)
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
        listener<RenderEvent.DynamicESP> {
            val entity = getClosestEntity<LivingEntity>(player.pos, 8.0) ?: return@listener
            it.renderer.build(entity.dynamicBox, filledColor, outlineColor)
        }

        listener<RenderEvent.StaticESP> {
            it.renderer.build(Box.of(player.pos, 0.3, 0.3, 0.3), filledColor, outlineColor)
        }
    }
}