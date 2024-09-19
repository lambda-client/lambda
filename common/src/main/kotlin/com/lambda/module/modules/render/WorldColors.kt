package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.vec3d
import net.minecraft.util.math.Vec3d
import java.awt.Color

object WorldColors : Module(
    name = "World Colors",
    description = "Changes the color of the sky",
    defaultTags = setOf(ModuleTag.RENDER)
){
    @JvmStatic
    val customSky by setting("Custom Sky", true)
    @JvmStatic
    val skyColor by setting("Sky Color", Color(255, 24, 75), "The color of your sky") { customSky }
    @JvmStatic
    val customFog by setting("Custom Fog",false)
    @JvmStatic
    val fogColor by setting("Fog Color", Color(255, 24, 75, 255), "The color of your fog") { customFog }
    @JvmStatic
    val customClouds by setting("Custom Clouds", false)
    @JvmStatic
    val cloudColor by setting("Cloud Color", Color(255, 24, 75)) { customClouds }

    @JvmStatic
    fun backgroundColor(base: Vec3d) =
        if (customFog && isEnabled) fogColor.vec3d else base
}
