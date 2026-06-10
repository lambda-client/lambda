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

package com.lambda.graphics.mc

import net.minecraft.util.Identifier

data class ItemOverlay(
    val texture: Identifier,
    val scale: Float = 1.0f,
    val speed: Float = 1.0f,
    val angle: Float = 0f,
    val alpha: Float = 0.5f
) {
    companion object {
        val EnchantGlint = ItemOverlay(
            texture = Identifier.of("minecraft", "textures/misc/enchanted_glint_item.png"),
            scale = 8.0f,
            speed = 1.0f,
            angle = 10f,
            alpha = 0.5f
        )
        
        val EntityGlint = ItemOverlay(
            texture = Identifier.of("minecraft", "textures/misc/enchanted_glint_armor.png"),
            scale = 8.0f,
            speed = 1.0f,
            angle = 10f,
            alpha = 0.5f
        )

        val Disabled = ItemOverlay(
            texture = Identifier.of("minecraft", "textures/misc/unknown.png"),
            alpha = 0f
        )
    }
}
