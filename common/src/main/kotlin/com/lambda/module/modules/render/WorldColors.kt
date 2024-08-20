package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import java.awt.Color

object WorldColors : Module(
    name = "World Colors",
    description = "Changes the color of the sky",
    defaultTags = setOf(ModuleTag.RENDER)
){
    @JvmStatic
    val customSky by setting("Sky Color", true)
    @JvmStatic
    val skyColor by setting("Color", Color(255, 24, 75), "The color of your sky") { customSky }
    @JvmStatic
    val customFog by setting("Fog",false)
    @JvmStatic
    val fogColor by setting("Color", Color(255, 24, 75, 255), "The color of your horizon") { customFog }
}