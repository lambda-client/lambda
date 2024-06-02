package com.lambda.gui.api.component.button

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Vec2d

abstract class ListButton(owner: ChildLayer.Drawable<*, *>) : ButtonComponent(owner) {
    override val position get() = Vec2d(0.0, lerp(0.0, renderHeightOffset, owner.childShowAnimation))
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