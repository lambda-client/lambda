package com.lambda.gui.api.component

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.gl.Scissor.scissor
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildComponent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.api.RenderLayer
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color
import kotlin.math.abs

abstract class WindowComponent <T : ChildComponent> (
    val gui: AbstractClickGui
) : ChildComponent(gui.windows) {
    abstract val title: String

    abstract var width: Double
    abstract var height: Double

    var position = Vec2d.ZERO

    var isOpen = true
    override val isActive get() = isOpen

    private var dragOffset: Vec2d? = null
    private val padding get() = ClickGui.windowPadding

    final override val rect get() = Rect.basedOn(position, width, renderHeight + titleBarHeight)
    private val contentRect get() = rect.shrink(padding).moveFirst(Vec2d(0.0, titleBarHeight - padding))

    private val titleBar get() = Rect.basedOn(rect.leftTop, rect.size.x, titleBarHeight)
    private val titleBarHeight get() = ClickGui.buttonHeight * 1.25

    private val renderer = RenderLayer()
    private val contentRenderer = RenderLayer()

    private val animation = gui.animation

    private val showAnimation by animation.exp(0.0, 1.0, 0.6, ::isOpen)
    override val childShowAnimation get() = lerp(0.0, showAnimation, gui.childShowAnimation)

    private val actualHeight get() = height + padding * 2 * isOpen.toInt()
    private var renderHeightAnimation by animation.exp({ 0.0 }, ::actualHeight, 0.6, ::isOpen)
    private val renderHeight get() = lerp(0.0, renderHeightAnimation, childShowAnimation)

    open val contentComponents = ChildLayer.Drawable<T, WindowComponent<T>>(gui, this, contentRenderer, ::contentRect)

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.Show -> {
                dragOffset = null
            }

            is GuiEvent.Render -> {
                // TODO: fix blur
                // BlurPostProcessor.render(rect, ClickGui.windowBlur, guiAnimation)

                val alpha = (gui.childShowAnimation * 2.0).coerceIn(0.0, 1.0)

                // Background
                renderer.filled.build(
                    rect = rect,
                    roundRadius = ClickGui.windowRadius,
                    color = GuiSettings.backgroundColor.multAlpha(alpha),
                    shade = GuiSettings.shadeBackground
                )

                // Outline
                renderer.outline.build(
                    rect = rect,
                    roundRadius = ClickGui.windowRadius,
                    innerGlow = ClickGui.windowRadius.coerceAtMost(1.0),
                    outerGlow = ClickGui.windowRadius,
                    color = GuiSettings.mainColor.multAlpha(alpha),
                    shade = GuiSettings.shadeBackground
                )

                // Title
                renderer.font.build(
                    text = title,
                    position = titleBar.center - Vec2d(renderer.font.getWidth(title) * 0.5, 0.0),
                    color = Color.WHITE.setAlpha(gui.childShowAnimation)
                )

                renderer.render()

                scissor(contentRect) {
                    contentComponents.onEvent(e)
                    contentRenderer.render()
                }

                return
            }

            is GuiEvent.MouseMove -> {
                dragOffset?.let {
                    position = e.mouse - it
                }
            }

            is GuiEvent.MouseClick -> {
                dragOffset = null

                if (e.mouse in titleBar && e.action == Mouse.Action.Click) {
                    when (e.button) {
                        Mouse.Button.Left -> dragOffset = e.mouse - position
                        Mouse.Button.Right -> {
                            // Don't let user spam
                            val targetHeight = if (isOpen) actualHeight else 0.0
                            if (abs(targetHeight - renderHeight) > 1) return

                            isOpen = !isOpen

                            if (isOpen) onEvent(GuiEvent.Show())
                        }
                    }
                }
            }
        }

        contentComponents.onEvent(e)
    }

    fun focus() {
        // move window into foreground
        gui.apply {
            scheduleAction {
                windows.children.apply {
                    this@WindowComponent
                        .apply(::remove)
                        .apply(::add)
                }
            }
        }
    }
}
