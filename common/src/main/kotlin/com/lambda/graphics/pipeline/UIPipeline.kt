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

package com.lambda.graphics.pipeline

import com.lambda.core.Loadable
import com.lambda.graphics.gl.GlStateUtils.withDepth
import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.rect.FilledRectRenderer
import com.lambda.graphics.renderer.gui.rect.OutlineRectRenderer

object UIPipeline : Loadable {
    private var uiDepth = 0
    val depth get() = uiDepth * -0.001

    fun objectDrawn() {
        uiDepth++
    }

    fun reset() {
        uiDepth = 0
    }

    fun render() = withDepth(true) {
        FilledRectRenderer.render()
        OutlineRectRenderer.render()
        FontRenderer.render()
    }
}