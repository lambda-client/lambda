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

package com.lambda.module.hud

import com.lambda.graphics.renderer.gui.TextureRenderer.drawTexture
import com.lambda.graphics.renderer.gui.TextureRenderer.drawTextureShaded
import com.lambda.graphics.texture.TextureOwner.upload
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag

object Watermark : HudModule(
    name = "Watermark",
    defaultTags = setOf(ModuleTag.CLIENT),
) {
    private val shade by setting("Shade", true)

    private val normalTexture = upload("textures/lambda.png")
    private val monoTexture = upload("textures/lambda_mono.png")

    init {
        build {
            width = 50.0
            height = 50.0

            customDrawable {
                if (shade) drawTextureShaded(monoTexture, rect)
                else drawTexture(normalTexture, rect)
            }
        }
    }
}
