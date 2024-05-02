package com.lambda.gui.api.component

import com.lambda.Lambda.mc
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.gl.Scissor.scissor
import com.lambda.graphics.renderer.gui.font.IFontEntry
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildComponent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.api.layer.RenderLayer
import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.lambda.util.primitives.extension.partialTicks
import java.awt.Color
import kotlin.math.abs

abstract class WindowComponent <T : ChildComponent> (
    final override val owner: AbstractClickGui
) : ChildComponent() {
    abstract val title: String

    abstract var width: Double
    abstract var height: Double

    var position = Vec2d.ZERO
    private var prevPosition = position

    var isOpen = true
    private var dragOffset: Vec2d? = null
    private val padding get() = ClickGui.windowPadding

    final override val rect get() = Rect.basedOn(renderPosition, width, renderHeight + titleBarHeight)
    val contentRect get() = rect.shrink(padding).moveFirst(Vec2d(0.0, titleBarHeight - padding))

    private val titleBar get() = Rect.basedOn(rect.leftTop, rect.size.x, titleBarHeight)
    private val titleBarHeight get() = titleFont.height + 2 + padding * 2
    private val titleFont: IFontEntry

    private val layer = RenderLayer()
    private val renderer = layer.entry()
    val subLayer = RenderLayer()

    val animation = owner.animation
    open val showAnimation get() = owner.showAnimation

    private val actualHeight get() = height + padding * 2 * isOpen.toInt()
    private var renderHeightAnimation by animation.exp({ 0.0 }, ::actualHeight, 0.6, ::isOpen)
    private val renderHeight get() = lerp(0.0, renderHeightAnimation, showAnimation)
    private val renderPosition get() = lerp(prevPosition, position, mc.partialTicks)

    val contentComponents = ChildLayer<T> { child ->
        child.rect in contentRect && accessible && isOpen
    }

    /*val titleBarComponents = ChildLayer<ButtonComponent> { child ->
        child.rect in titleBar && accessible
    }*/ // TODO: window close button

    init {
        // Background
        renderer.rect {
            position = rect
            roundRadius = ClickGui.windowRadius

            val alpha = (showAnimation * 2.0).coerceIn(0.0, 1.0)
            color(GuiSettings.backgroundColor.multAlpha(alpha))
        }

        // Title
        titleFont = renderer.font {
            text = title
            position = titleBar.center - widthVec * 0.5
            color = Color.WHITE.setAlpha(showAnimation)
        }
    }

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.Show -> {
                dragOffset = null
            }

            is GuiEvent.Tick -> {
                prevPosition = position
            }

            is GuiEvent.Render -> {
                layer.assignOffset(renderPosition)
                subLayer.assignOffset(renderPosition)

                // TODO: fix blur
                // BlurPostProcessor.render(rect, ClickGui.windowBlur, guiAnimation)

                layer.render()

                scissor(contentRect) {
                    subLayer.apply {
                        allowEffects = true
                        render()
                    }
                }
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

                            if (isOpen) contentComponents.onEvent(GuiEvent.Show())
                        }
                    }
                }
            }
        }

        contentComponents.onEvent(e)
        //titleBarComponents.onEvent(e)
    }

    fun forceSetPosition(pos: Vec2d) {
        position = pos
        prevPosition = position
    }

    fun focus() {
        // move window into foreground
        owner.apply {
            scheduleAction {
                windows.children.apply {
                    this@WindowComponent
                        .apply(::remove)
                        .apply(::add)
                }
            }
        }
    }

    fun destroy() {
        owner.apply {
            scheduleAction {
                windows.removeChild(this@WindowComponent)
            }
        }
    }

    override fun onRemove() {
        layer.destroy()
        subLayer.destroy()
    }
}
