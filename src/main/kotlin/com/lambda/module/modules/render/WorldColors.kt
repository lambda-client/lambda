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

package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.vec3d
import net.minecraft.util.math.Vec3d
import java.awt.Color

object WorldColors : Module(
    name = "WorldColors",
    description = "Changes the color of the sky, clouds and fog",
    tag = ModuleTag.RENDER,
) {
    @JvmStatic
    val customSky by setting("Custom Sky", true)

    @JvmStatic
    val skyColor by setting("Sky Color", Color(255, 24, 75), "The color of your sky") { customSky }

    @JvmStatic
    val customFogOfWar by setting("Custom Fog of War", false)

    @JvmStatic
    val fogOfWarColor by setting("Fog of War Color", Color(255, 24, 75, 255), "The color of your fog") { customFogOfWar }

    @JvmStatic
    val customClouds by setting("Custom Clouds", false)

    @JvmStatic
    val cloudColor by setting("Cloud Color", Color(255, 24, 75)) { customClouds }

    @JvmStatic
    val customWaterFog by setting("Custom Water Fog", true)

    @JvmStatic
    val waterFogColor by setting("Water Fog Color", Color(255, 24, 75, 255), "The color of your water fog") { customWaterFog }

    @JvmStatic
    fun fogOfWarColor(base: Vec3d) =
        if (customFogOfWar && isEnabled) fogOfWarColor.vec3d else base

    @JvmStatic
    fun waterFogColor(base: Int): Int =
        if (customWaterFog && isEnabled) this.waterFogColor.rgb else base
}
