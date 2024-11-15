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

package com.lambda.gui.api

import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.rect.FilledRectRenderer
import com.lambda.graphics.renderer.gui.rect.OutlineRectRenderer
import com.lambda.threading.mainThread

class RenderLayer {
    val filled by mainThread(::FilledRectRenderer)
    val outline by mainThread(::OutlineRectRenderer)

    // TODO: CHANGE BOTH OF THESE!!!!
    // I do NOT want to see 110 vbos
    val font by mainThread { FontRenderer() }
    private val boldFont0 = lazy { FontRenderer() }

    val boldFont by boldFont0

    fun render() {
        filled.render()
        outline.render()
        font.render()

        if (boldFont0.isInitialized()) boldFont0.value.render()
    }
}
