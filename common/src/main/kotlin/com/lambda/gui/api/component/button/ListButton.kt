package com.lambda.gui.api.component.button

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.WindowComponent
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Vec2d

abstract class ListButton(owner: WindowComponent<*>) : ButtonComponent(owner) {
    override val position get() = Vec2d(0.0, renderHeightOffset)
    override val size get() = Vec2d(FILL_PARENT, ClickGui.buttonHeight)

    var heightOffset = 0.0
    private val targetHeightOffset get() = heightOffset * owner.showAnimation * owner.isOpen.toInt()
    private var renderHeightOffset by animation.exp(::targetHeightOffset, 0.5)

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        if (e is GuiEvent.Show) renderHeightOffset = 0.0
    }
}