package com.lambda.gui.impl.clickgui

import com.lambda.gui.GuiConfigurable
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.RenderLayer
import com.lambda.gui.api.component.button.ButtonComponent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.gui.impl.hudgui.LambdaHudGui
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.client.GuiSettings.primaryColor
import com.lambda.util.math.multAlpha
import com.lambda.util.math.Vec2d
import java.awt.Color

object LambdaClickGui : AbstractClickGui("ClickGui", ClickGui) {
    override val moduleFilter: (Module) -> Boolean = {
        it !is HudModule
    }

    override val configurable get() = GuiConfigurable

    private val buttonRenderer = RenderLayer()
    private val buttons = ChildLayer.Drawable<ButtonComponent, AbstractClickGui>(this, this, buttonRenderer, ::rect) {
        hoveredWindow == null && !closing
    }

    override fun onEvent(e: GuiEvent) {
        buttons.onEvent(e)
        if (e is GuiEvent.Render) buttonRenderer.render()

        super.onEvent(e)
    }

    init {
        buttons.children.add(object : ButtonComponent(buttons) {
            override val position: Vec2d get() = screenSize - size - Vec2d.ONE * 5.0
            override val size = Vec2d(30.0, 15.0)
            override val text = "HUD"
            override val centerText = true
            override val roundRadius = ClickGui.windowRadius

            override var activeAnimation; get() = pressAnimation; set(_) {}

            override fun onEvent(e: GuiEvent) {
                super.onEvent(e)

                if (e is GuiEvent.Render) {
                    val rect = rect.shrink(interactAnimation)

                    // Background
                    renderer.filled.build(
                        rect = rect,
                        roundRadius = ClickGui.windowRadius,
                        color = GuiSettings.backgroundColor.multAlpha(childShowAnimation),
                        shade = GuiSettings.shadeBackground
                    )

                    // Outline
                    renderer.outline.build(
                        rect = rect,
                        roundRadius = ClickGui.windowRadius,
                        glowRadius = ClickGui.glowRadius,
                        color = (if (GuiSettings.shadeBackground) Color.WHITE else primaryColor).multAlpha(childShowAnimation),
                        shade = GuiSettings.shadeBackground
                    )
                }
            }

            override fun performClickAction(e: GuiEvent.MouseClick) {
                LambdaHudGui.show()
            }
        })
    }
}
