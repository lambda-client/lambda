package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.world.DirectionMask.mask
import com.lambda.graphics.renderer.world.core.box.DynamicFilledRenderer
import com.lambda.graphics.renderer.world.core.box.DynamicOutlineRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.ColorUtils.a
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.MathUtils.toIntSign
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.util.math.Box
import java.awt.Color

object RenderTest : Module(
    name = "RenderTest",
    description = "RenderTest",
    defaultTags = setOf(ModuleTag.DEBUG)
) {
    private val test1 by setting("Toggle visibility", true)
    private val test21 by setting("Hallo 1", true, visibility = ::test1)
    private val test22 by setting("Hallo 2", true, visibility = ::test1)
    private val test23 by setting("Hallo 3", true, visibility = ::test1)
    private val test31 by setting("Holla huh 1", true, visibility = { !test1 })
    private val test32 by setting("Holla buh 2", true, visibility = { !test1 })

    private val filled = DynamicFilledRenderer()
    private val outline = DynamicOutlineRenderer()
    private val color = Color(60, 200, 60)

    init {
        filled.build {
            val flag = mc.crosshairTarget?.blockResult?.blockPos?.let { box = Box(it) } != null
            color = this@RenderTest.color.setAlpha((color.a + flag.toIntSign() * 0.05).coerceAtMost(0.2))
        }

        outline.build {
            val block = mc.crosshairTarget?.blockResult
            val flag = block?.blockPos?.let { box = Box(it) } != null
            color = this@RenderTest.color.setAlpha(color.a + flag.toIntSign() * 0.2)
            sides = block?.side?.mask ?: sides
        }

        listener<TickEvent.Pre> {
            filled.update()
            outline.update()
        }

        listener<RenderEvent.World> {
            filled.render()
            outline.render()
        }
    }
}