package com.lambda.module.modules.debug

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

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

//    private val filled = DynamicFilledRenderer()
//    private val outline = DynamicOutlineRenderer()
//    private val color = Color(60, 200, 60)
//
//    init {
//        filled.build {
//            val flag = mc.crosshairTarget?.blockResult?.blockPos?.let { box = Box(it) } != null
//            color = color.setAlpha((color.a + flag.toIntSign() * 0.05).coerceAtMost(0.2))
//        }
//
//        outline.build {
//            val block = mc.crosshairTarget?.blockResult
//            val flag = block?.blockPos?.let { box = Box(it) } != null
//            color = color.setAlpha(color.a + flag.toIntSign() * 0.2)
//            sides = block?.side?.mask ?: sides
//        }
//
//        listener<TickEvent.Pre> {
//            filled.update()
//            outline.update()
//        }
//
//        listener<RenderEvent.World> {
//            filled.render()
//            outline.render()
//        }
//    }
}