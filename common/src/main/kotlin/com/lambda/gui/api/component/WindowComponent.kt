package com.lambda.gui.api.component

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.gl.Scissor.scissor
import com.lambda.graphics.renderer.gui.font.IFontEntry
import com.lambda.graphics.renderer.immediate.BlurPostProcessor
import com.lambda.gui.api.component.core.list.IListComponent
import com.lambda.gui.api.component.core.list.ChildComponent
import com.lambda.gui.api.layer.RenderLayer
import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.KeyCode
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
    final override val owner: AbstractClickGui
) : ChildComponent(), IListComponent<T> {
    abstract val title: String

    abstract var width: Double
    abstract var height: Double

    var position = Vec2d.ZERO

    var isOpen = true
    private var dragOffset: Vec2d? = null
    private val padding get() = ClickGui.windowPadding

    final override val rect get() = Rect.basedOn(position, width, renderHeight + titleBarHeight)
    val contentRect get() = rect.shrink(padding).moveFirst(Vec2d(0.0, titleBarHeight - padding))

    private val titleBar get() = Rect.basedOn(rect.leftTop, rect.size.x, titleBarHeight)
    private val titleBarHeight get() = titleFont.height + 2 + padding * 2
    private val titleFont: IFontEntry

    private val layer = RenderLayer()
    private val renderer = layer.entry()
    val subLayer = RenderLayer()

    val animation = owner.animation
    val guiAnimation get() = owner.guiAnimation

    private val actualHeight get() = height + padding * 2 * isOpen.toInt()
    private var renderHeightAnimation by animation.exp({ 0.0 }, ::actualHeight, 0.6, ::isOpen)
    private val renderHeight get() = lerp(0.0, renderHeightAnimation, guiAnimation)

    override val children = mutableListOf<T>()

    init {
        // Background
        renderer.rect {
            position = rect
            roundRadius = ClickGui.windowRadius

            val alpha = (guiAnimation * 2.0).coerceIn(0.0, 1.0)
            color(GuiSettings.backgroundColor.multAlpha(alpha))
        }

        // Title
        titleFont = renderer.font {
            text = title
            position = titleBar.center - widthVec * 0.5
            color = Color.WHITE.setAlpha(guiAnimation)
        }
    }

    override fun onShow() {
        super<ChildComponent>.onShow()
        super<IListComponent>.onShow()

        dragOffset = null
    }

    override fun onHide() {
        super<IListComponent>.onHide()
    }

    override fun onTick() {
        children.forEach { child ->
            child.accessible = child.rect in contentRect && this.accessible
        }

        super<IListComponent>.onTick()
    }

    override fun onRender() {
        BlurPostProcessor.render(rect, 15) // TODO: Customizable blur level
        layer.render()

        scissor(contentRect) {
            subLayer.apply {
                allowEffects = true
                render()
            }

            super<IListComponent>.onRender()
        }
    }

    override fun onMouseMove(mouse: Vec2d) {
        dragOffset?.let {
            position = mouse - it
        }

        super<ChildComponent>.onMouseMove(mouse)
        super<IListComponent>.onMouseMove(mouse)
    }

    override fun onKey(key: KeyCode) {
        super<IListComponent>.onKey(key)
    }

    override fun onChar(char: Char) {
        super<IListComponent>.onChar(char)
    }

    override fun onMouseClick(button: Mouse.Button, action: Mouse.Action, mouse: Vec2d) {
        super<ChildComponent>.onMouseClick(button, action, mouse)

        dragOffset = null

        if (mouse in titleBar && action == Mouse.Action.Click) {
            when(button) {
                Mouse.Button.Left -> dragOffset = mouse - position
                Mouse.Button.Right -> {
                    // Don't let user spam
                    val targetHeight = if (isOpen) actualHeight else 0.0
                    if (abs(targetHeight - renderHeight) > 1) return

                    isOpen = !isOpen

                    if (isOpen) super<IListComponent>.onShow()
                }
            }
        }

        super<IListComponent>.onMouseClick(button, action, mouse)
    }

    fun destroy() {
        layer.destroy()
        subLayer.destroy()
        children.clear()
    }
}
