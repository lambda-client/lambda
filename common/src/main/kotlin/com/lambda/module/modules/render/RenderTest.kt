package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object RenderTest : Module(
    name = "RenderTest",
    description = "RenderTest",
    defaultTags = setOf(ModuleTag.DEBUG)
) {
    private val test1 by setting("Toggle visibility", true)
    private val test21 by setting("Hallo 1", true, visibility = ::test1)
    private val test22 by setting("Hallo Slider", 1.0, 0.0..5.0, 0.5, visibility = ::test1)
    private val test23 by setting("Hallo String", "bruh", visibility = ::test1)
    private val test31 by setting("Holla huh 1", true, visibility = { !test1 })
    private val test32 by setting("Holla buh 2", true, visibility = { !test1 })
}