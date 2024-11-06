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

package com.lambda.gui.api.component.button

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp

abstract class ListButton(owner: ChildLayer.Drawable<*, *>) : ButtonComponent(owner) {
    override val position get() = Vec2d(0.0, lerp(owner.childShowAnimation, 0.0, renderHeightOffset))
    override val size get() = Vec2d(FILL_PARENT, ClickGui.buttonHeight)

    open val listStep get() = ClickGui.buttonStep

    var heightOffset = 0.0
    protected var renderHeightAnimation by animation.exp(::heightOffset, 0.8)
    protected open val renderHeightOffset get() = renderHeightAnimation

    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Show) {
            heightOffset = 0.0
            renderHeightAnimation = 0.0
        }
        super.onEvent(e)
    }
}
