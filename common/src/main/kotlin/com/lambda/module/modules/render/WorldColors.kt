/*
 * Copyright 2024 Lambda
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

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.vec3d
import net.minecraft.util.math.Vec3d
import java.awt.Color

object WorldColors : Module(
    name = "WorldColors",
    description = "Changes the color of the sky",
    defaultTags = setOf(ModuleTag.RENDER)
) {
    @JvmStatic
    val customSky by setting("Custom Sky", true)

    @JvmStatic
    val skyColor by setting("Sky Color", Color(255, 24, 75), "The color of your sky") { customSky }

    @JvmStatic
    val customFog by setting("Custom Fog", false)

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
